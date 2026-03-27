package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.agent.AgentSystemPrompts;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.*;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.FirmProfileRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradingAgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing firm profiles and spawning default agent rosters.
 *
 * <p>Each user owns a single firm. The firm type determines which agents
 * are created with pre-assigned names, roles, colors, and schedules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmProfileService {

    private final FirmProfileRepository firmProfileRepository;
    private final TradingAgentRepository tradingAgentRepository;

    /** Default persona color palette for agents */
    private static final String COLOR_BLUE = "#185FA5";
    private static final String COLOR_RED = "#A5182E";
    private static final String COLOR_GREEN = "#18A55F";
    private static final String COLOR_PURPLE = "#7B18A5";
    private static final String COLOR_AMBER = "#A58B18";
    private static final String COLOR_TEAL = "#18A5A0";
    private static final String COLOR_CYAN = "#18A5D6";
    private static final String COLOR_INDIGO = "#4318A5";
    private static final String COLOR_ORANGE = "#A56218";
    private static final String COLOR_PINK = "#A51876";

    /**
     * Create a new firm profile for a user and spawn the default agent roster.
     *
     * @param userId       the owner user's database ID
     * @param firmName     display name for the firm (e.g. "Apex Capital")
     * @param type         the firm type (HEDGE_FUND, HFT, etc.)
     * @param capital      initial capital allocation
     * @param riskAppetite risk level: CONSERVATIVE, MODERATE, or AGGRESSIVE
     * @return the saved FirmProfile
     */
    @Transactional
    public FirmProfile createFirm(Long userId, String firmName, FirmType type,
                                  BigDecimal capital, String riskAppetite) {
        // Check if firm already exists for user
        firmProfileRepository.findByOwnerUserId(userId).ifPresent(f -> {
            throw new IllegalStateException("Firm already exists for user: " + userId);
        });

        FirmProfile firm = FirmProfile.builder()
                .firmName(firmName)
                .firmType(type)
                .initialCapital(capital)
                .riskAppetite(riskAppetite != null ? riskAppetite : "MODERATE")
                .setupComplete(false)
                .ownerUserId(userId)
                .build();

        FirmProfile saved = firmProfileRepository.save(firm);
        log.info("Created firm: id={}, name={}, type={}, owner={}",
                saved.getId(), saved.getFirmName(), saved.getFirmType(), userId);

        // Spawn agents based on firm type
        spawnDefaultAgentsForFirm(saved);

        // Mark setup as complete
        saved.setSetupComplete(true);
        saved.setUpdatedAt(Instant.now());
        firmProfileRepository.save(saved);

        return saved;
    }

    /**
     * Get the firm profile for a given user.
     *
     * @param userId the owner user ID
     * @return the firm profile if it exists
     */
    @Transactional(readOnly = true)
    public Optional<FirmProfile> getFirmForUser(Long userId) {
        return firmProfileRepository.findByOwnerUserId(userId);
    }

    /**
     * Check if a user has completed firm setup.
     *
     * @param userId the owner user ID
     * @return true if firm exists and setup is complete
     */
    @Transactional(readOnly = true)
    public boolean isSetupComplete(Long userId) {
        return firmProfileRepository.findByOwnerUserId(userId)
                .map(FirmProfile::getSetupComplete)
                .orElse(false);
    }

    /**
     * Mark a user's firm setup as complete.
     *
     * @param userId the owner user ID
     * @return the updated firm profile
     */
    @Transactional
    public FirmProfile completeFirmSetup(Long userId) {
        FirmProfile firm = firmProfileRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No firm found for user: " + userId));
        firm.setSetupComplete(true);
        firm.setUpdatedAt(Instant.now());
        return firmProfileRepository.save(firm);
    }

    /**
     * Update firm name and/or risk appetite.
     *
     * @param userId       the owner user ID
     * @param firmName     new firm name (null to keep existing)
     * @param riskAppetite new risk appetite (null to keep existing)
     * @return the updated firm profile
     */
    @Transactional
    public FirmProfile updateFirm(Long userId, String firmName, String riskAppetite) {
        FirmProfile firm = firmProfileRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No firm found for user: " + userId));
        if (firmName != null) {
            firm.setFirmName(firmName);
        }
        if (riskAppetite != null) {
            firm.setRiskAppetite(riskAppetite);
        }
        firm.setUpdatedAt(Instant.now());
        return firmProfileRepository.save(firm);
    }

    // ── Agent Spawning ──────────────────────────────────────────────────

    /**
     * Spawn the default agent roster for a firm based on its type.
     * Each agent gets a human persona name, role-specific color, and schedule.
     *
     * @param firm the firm profile to spawn agents for
     */
    private void spawnDefaultAgentsForFirm(FirmProfile firm) {
        List<AgentSpawnSpec> specs = getAgentSpecsForFirmType(firm.getFirmType());

        for (AgentSpawnSpec spec : specs) {
            String systemPrompt = getDefaultPromptForRole(spec.role);
            TradingAgent agent = TradingAgent.builder()
                    .name(spec.name)
                    .strategyId(spec.strategyId)
                    .cronExpression(spec.cron)
                    .agentRole(spec.role)
                    .systemPrompt(systemPrompt)
                    .personaName(spec.name)
                    .personaColor(spec.color)
                    .personaInitials(spec.initials)
                    .active(false)
                    .build();

            tradingAgentRepository.save(agent);
            log.info("Spawned agent: name={}, role={}, firmType={}",
                    spec.name, spec.role, firm.getFirmType());
        }
    }

    /**
     * Get the agent spawn specifications for a given firm type.
     *
     * @param firmType the firm type
     * @return list of agent specifications to create
     */
    private List<AgentSpawnSpec> getAgentSpecsForFirmType(FirmType firmType) {
        return switch (firmType) {
            case HEDGE_FUND -> List.of(
                new AgentSpawnSpec("Marcus K.", AgentRole.QUANT_RESEARCHER, 1L,
                        "0 0 9 * * MON-FRI", COLOR_BLUE, "MK"),
                new AgentSpawnSpec("Sofia R.", AgentRole.RISK_ANALYST, 1L,
                        "0 30 9 * * MON-FRI", COLOR_RED, "SR"),
                new AgentSpawnSpec("James P.", AgentRole.PORTFOLIO_CONSTRUCTOR, 2L,
                        "0 0 10 * * MON-FRI", COLOR_GREEN, "JP"),
                new AgentSpawnSpec("Anika N.", AgentRole.BIAS_AUDITOR, 1L,
                        "0 0 16 * * MON-FRI", COLOR_PURPLE, "AN"),
                new AgentSpawnSpec("Chen W.", AgentRole.PERFORMANCE_ATTRIBUTOR, 1L,
                        "0 0 17 * * MON-FRI", COLOR_TEAL, "CW")
            );

            case HFT -> List.of(
                new AgentSpawnSpec("Raj M.", AgentRole.HFT_SYSTEMS_ENGINEER, 1L,
                        "0 */5 9-16 * * MON-FRI", COLOR_ORANGE, "RM"),
                new AgentSpawnSpec("Lena F.", AgentRole.EXECUTION_MONITOR, 1L,
                        "0 */1 9-16 * * MON-FRI", COLOR_PINK, "LF"),
                new AgentSpawnSpec("Omar S.", AgentRole.MARKET_REGIME_ANALYST, 3L,
                        "0 0 9 * * MON-FRI", COLOR_CYAN, "OS"),
                new AgentSpawnSpec("Yuki T.", AgentRole.EXECUTION_OPTIMIZER, 1L,
                        "0 */10 9-16 * * MON-FRI", COLOR_INDIGO, "YT")
            );

            case PROP_TRADING -> List.of(
                new AgentSpawnSpec("Alex D.", AgentRole.QUANT_RESEARCHER, 2L,
                        "0 0 9 * * MON-FRI", COLOR_BLUE, "AD"),
                new AgentSpawnSpec("Nina B.", AgentRole.EXECUTION_OPTIMIZER, 1L,
                        "0 */15 9-16 * * MON-FRI", COLOR_INDIGO, "NB"),
                new AgentSpawnSpec("Paulo R.", AgentRole.RISK_ANALYST, 1L,
                        "0 0 9 * * MON-FRI", COLOR_RED, "PR"),
                new AgentSpawnSpec("Zoe L.", AgentRole.PSYCHOLOGY_ENFORCER, 1L,
                        "0 0 8 * * MON-FRI", COLOR_AMBER, "ZL")
            );

            case GLOBAL_MACRO -> List.of(
                new AgentSpawnSpec("Isabel M.", AgentRole.MARKET_REGIME_ANALYST, 4L,
                        "0 0 8 * * MON-FRI", COLOR_CYAN, "IM"),
                new AgentSpawnSpec("Theo G.", AgentRole.QUANT_RESEARCHER, 4L,
                        "0 0 9 * * MON-FRI", COLOR_BLUE, "TG"),
                new AgentSpawnSpec("Priya S.", AgentRole.PORTFOLIO_CONSTRUCTOR, 2L,
                        "0 0 10 * * MON-FRI", COLOR_GREEN, "PS"),
                new AgentSpawnSpec("Lars H.", AgentRole.RISK_ANALYST, 1L,
                        "0 30 9 * * MON-FRI", COLOR_RED, "LH")
            );

            case MULTI_STRATEGY -> List.of(
                new AgentSpawnSpec("Marcus K.", AgentRole.QUANT_RESEARCHER, 1L,
                        "0 0 9 * * MON-FRI", COLOR_BLUE, "MK"),
                new AgentSpawnSpec("Sofia R.", AgentRole.RISK_ANALYST, 1L,
                        "0 30 9 * * MON-FRI", COLOR_RED, "SR"),
                new AgentSpawnSpec("James P.", AgentRole.PORTFOLIO_CONSTRUCTOR, 2L,
                        "0 0 10 * * MON-FRI", COLOR_GREEN, "JP"),
                new AgentSpawnSpec("Anika N.", AgentRole.BIAS_AUDITOR, 1L,
                        "0 0 16 * * MON-FRI", COLOR_PURPLE, "AN"),
                new AgentSpawnSpec("Chen W.", AgentRole.PERFORMANCE_ATTRIBUTOR, 1L,
                        "0 0 17 * * MON-FRI", COLOR_TEAL, "CW"),
                new AgentSpawnSpec("Raj M.", AgentRole.HFT_SYSTEMS_ENGINEER, 1L,
                        "0 */5 9-16 * * MON-FRI", COLOR_ORANGE, "RM"),
                new AgentSpawnSpec("Lena F.", AgentRole.EXECUTION_MONITOR, 1L,
                        "0 */1 9-16 * * MON-FRI", COLOR_PINK, "LF"),
                new AgentSpawnSpec("Omar S.", AgentRole.MARKET_REGIME_ANALYST, 3L,
                        "0 0 9 * * MON-FRI", COLOR_CYAN, "OS"),
                new AgentSpawnSpec("Yuki T.", AgentRole.EXECUTION_OPTIMIZER, 1L,
                        "0 */10 9-16 * * MON-FRI", COLOR_INDIGO, "YT"),
                new AgentSpawnSpec("Zoe L.", AgentRole.PSYCHOLOGY_ENFORCER, 1L,
                        "0 0 8 * * MON-FRI", COLOR_AMBER, "ZL")
            );

            case QUANT_FUND -> List.of(
                new AgentSpawnSpec("Marcus K.", AgentRole.QUANT_RESEARCHER, 1L,
                        "0 0 9 * * MON-FRI", COLOR_BLUE, "MK"),
                new AgentSpawnSpec("Sofia R.", AgentRole.RISK_ANALYST, 1L,
                        "0 30 9 * * MON-FRI", COLOR_RED, "SR"),
                new AgentSpawnSpec("James P.", AgentRole.PORTFOLIO_CONSTRUCTOR, 2L,
                        "0 0 10 * * MON-FRI", COLOR_GREEN, "JP"),
                new AgentSpawnSpec("Anika N.", AgentRole.BIAS_AUDITOR, 1L,
                        "0 0 16 * * MON-FRI", COLOR_PURPLE, "AN"),
                new AgentSpawnSpec("Omar S.", AgentRole.MARKET_REGIME_ANALYST, 3L,
                        "0 0 9 * * MON-FRI", COLOR_CYAN, "OS"),
                new AgentSpawnSpec("Chen W.", AgentRole.PERFORMANCE_ATTRIBUTOR, 1L,
                        "0 0 17 * * MON-FRI", COLOR_TEAL, "CW")
            );

            case ASSET_MANAGEMENT -> List.of(
                new AgentSpawnSpec("James P.", AgentRole.PORTFOLIO_CONSTRUCTOR, 2L,
                        "0 0 10 * * MON-FRI", COLOR_GREEN, "JP"),
                new AgentSpawnSpec("Sofia R.", AgentRole.RISK_ANALYST, 1L,
                        "0 30 9 * * MON-FRI", COLOR_RED, "SR"),
                new AgentSpawnSpec("Chen W.", AgentRole.PERFORMANCE_ATTRIBUTOR, 1L,
                        "0 0 17 * * MON-FRI", COLOR_TEAL, "CW"),
                new AgentSpawnSpec("Anika N.", AgentRole.BIAS_AUDITOR, 1L,
                        "0 0 16 * * MON-FRI", COLOR_PURPLE, "AN")
            );

            case RESEARCH_LAB -> List.of(
                new AgentSpawnSpec("Marcus K.", AgentRole.QUANT_RESEARCHER, 1L,
                        "0 0 9 * * MON-FRI", COLOR_BLUE, "MK"),
                new AgentSpawnSpec("Isabel M.", AgentRole.MARKET_REGIME_ANALYST, 4L,
                        "0 0 8 * * MON-FRI", COLOR_CYAN, "IM"),
                new AgentSpawnSpec("Anika N.", AgentRole.BIAS_AUDITOR, 1L,
                        "0 0 16 * * MON-FRI", COLOR_PURPLE, "AN")
            );

            case CUSTOM -> List.of();
        };
    }

    /**
     * Get the default system prompt for a given agent role.
     *
     * @param role the agent role
     * @return the system prompt string
     */
    private String getDefaultPromptForRole(AgentRole role) {
        return switch (role) {
            case QUANT_RESEARCHER -> AgentSystemPrompts.QUANT_RESEARCHER;
            case BIAS_AUDITOR -> AgentSystemPrompts.BIAS_AUDITOR;
            case RISK_ANALYST -> AgentSystemPrompts.RISK_ANALYST;
            case PORTFOLIO_CONSTRUCTOR -> AgentSystemPrompts.PORTFOLIO_CONSTRUCTOR;
            case PSYCHOLOGY_ENFORCER -> AgentSystemPrompts.PSYCHOLOGY_ENFORCER;
            case PERFORMANCE_ATTRIBUTOR -> AgentSystemPrompts.PERFORMANCE_ATTRIBUTOR;
            case MARKET_REGIME_ANALYST -> AgentSystemPrompts.MARKET_REGIME_ANALYST;
            case EXECUTION_OPTIMIZER -> AgentSystemPrompts.EXECUTION_OPTIMIZER;
            case HFT_SYSTEMS_ENGINEER -> AgentSystemPrompts.HFT_SYSTEMS_ENGINEER;
            case EXECUTION_MONITOR -> AgentSystemPrompts.EXECUTION_MONITOR;
        };
    }

    /**
     * Internal specification for an agent to be spawned during firm setup.
     */
    private record AgentSpawnSpec(
            String name,
            AgentRole role,
            Long strategyId,
            String cron,
            String color,
            String initials
    ) {}
}
