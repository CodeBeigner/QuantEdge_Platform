package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.agent.AgentSystemPrompts;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.ExecutionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Strategy;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.StrategyRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradingAgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Manages trading agent lifecycle: CRUD + cron-based execution scheduling.
 * Active agents execute their linked strategy on the configured cron.
 *
 * <p>Includes risk gate (BUG 1 fix) and AI role management.
 */
@Slf4j
@Service
public class AgentSchedulerService {

    private final TradingAgentRepository agentRepository;
    private final StrategyRepository strategyRepository;
    private final StrategyService strategyService;
    private final RiskEngineService riskEngineService;
    private final TaskScheduler taskScheduler;
    private final AgentStatusBroadcastService statusBroadcast;

    /** Tracks running agent futures for start/stop control */
    private final Map<Long, ScheduledFuture<?>> runningAgents = new ConcurrentHashMap<>();

    /** Max chars of system prompt to include in response preview */
    private static final int SYSTEM_PROMPT_PREVIEW_LENGTH = 200;

    public AgentSchedulerService(TradingAgentRepository agentRepository,
            StrategyRepository strategyRepository,
            StrategyService strategyService,
            RiskEngineService riskEngineService,
            TaskScheduler taskScheduler,
            AgentStatusBroadcastService statusBroadcast) {
        this.agentRepository = agentRepository;
        this.strategyRepository = strategyRepository;
        this.strategyService = strategyService;
        this.riskEngineService = riskEngineService;
        this.taskScheduler = taskScheduler;
        this.statusBroadcast = statusBroadcast;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────

    /**
     * Create a new trading agent with the given configuration.
     *
     * @param request the agent creation request (includes optional AI role)
     * @return the created agent as a response DTO
     */
    public TradingAgentResponse createAgent(TradingAgentRequest request) {
        AgentRole role = request.getAgentRole() != null
                ? request.getAgentRole() : AgentRole.QUANT_RESEARCHER;

        String systemPrompt = request.getCustomSystemPrompt() != null
                ? request.getCustomSystemPrompt()
                : getDefaultPromptForRole(role);

        TradingAgent agent = TradingAgent.builder()
                .name(request.getName())
                .strategyId(request.getStrategyId())
                .cronExpression(request.getCronExpression())
                .agentRole(role)
                .systemPrompt(systemPrompt)
                .build();

        TradingAgent saved = agentRepository.save(agent);
        log.info("Created agent: id={}, name={}, role={}", saved.getId(), saved.getName(), saved.getAgentRole());
        return toResponse(saved);
    }

    /**
     * List all trading agents.
     *
     * @return list of all agents as response DTOs
     */
    public List<TradingAgentResponse> getAllAgents() {
        return agentRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Get a specific agent by ID.
     *
     * @param id agent ID
     * @return agent response DTO
     * @throws IllegalArgumentException if not found
     */
    public TradingAgentResponse getAgent(Long id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * Delete an agent, stopping it first if active.
     *
     * @param id agent ID
     */
    public void deleteAgent(Long id) {
        stopAgent(id);
        agentRepository.delete(findOrThrow(id));
        log.info("Deleted agent: id={}", id);
    }

    // ── Role Management ─────────────────────────────────────────────────

    /**
     * Update an agent's AI role and set its system prompt to the default for that role.
     *
     * @param id   agent ID
     * @param role the new agent role
     * @return updated agent response DTO
     */
    public TradingAgentResponse updateAgentRole(Long id, AgentRole role) {
        TradingAgent agent = findOrThrow(id);
        agent.setAgentRole(role);
        agent.setSystemPrompt(getDefaultPromptForRole(role));
        agent.setUpdatedAt(Instant.now());
        agentRepository.save(agent);
        log.info("Updated agent role: id={}, role={}", id, role);
        return toResponse(agent);
    }

    // ── Start / Stop ────────────────────────────────────────────────────

    /**
     * Start scheduled cron execution for an agent.
     *
     * @param id agent ID
     * @return updated agent response DTO
     */
    public TradingAgentResponse startAgent(Long id) {
        TradingAgent agent = findOrThrow(id);

        if (runningAgents.containsKey(id)) {
            log.info("Agent {} is already running", id);
            return toResponse(agent);
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeAgentTick(agent),
                new CronTrigger(agent.getCronExpression()));

        runningAgents.put(id, future);
        agent.setActive(true);
        agent.setUpdatedAt(Instant.now());
        agentRepository.save(agent);

        log.info("Started agent: id={}, cron={}", id, agent.getCronExpression());
        return toResponse(agent);
    }

    /**
     * Stop scheduled cron execution for an agent.
     *
     * @param id agent ID
     * @return updated agent response DTO
     */
    public TradingAgentResponse stopAgent(Long id) {
        ScheduledFuture<?> future = runningAgents.remove(id);
        if (future != null) {
            future.cancel(false);
        }

        TradingAgent agent = findOrThrow(id);
        agent.setActive(false);
        agent.setUpdatedAt(Instant.now());
        agentRepository.save(agent);

        log.info("Stopped agent: id={}", id);
        return toResponse(agent);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    /**
     * Execute one tick of the agent's strategy, with risk gate checks.
     *
     * @param agent the trading agent performing this tick
     */
    private void executeAgentTick(TradingAgent agent) {
        String personaName = agent.getPersonaName() != null ? agent.getPersonaName() : agent.getName();
        String role = agent.getAgentRole() != null ? agent.getAgentRole().name() : "QUANT_RESEARCHER";

        try {
            // Broadcast: agent is starting research
            statusBroadcast.broadcastAgentStatus(agent.getId(), personaName,
                    role, "RESEARCHING", "Executing strategy tick...", null);

            // ── Risk Gate: check position limits ──
            Map<String, Object> positionCheck = riskEngineService.checkPositionLimits();
            boolean allClear = Boolean.TRUE.equals(positionCheck.get("allClear"));
            if (!allClear) {
                log.warn("Agent {} tick ABORTED: position limits breached. Details: {}",
                        agent.getId(), positionCheck.get("breaches"));
                statusBroadcast.broadcastAgentStatus(agent.getId(), personaName,
                        role, "ALERT", "Position limits breached — tick aborted", null);
                return;
            }

            // ── Risk Gate: check VaR for the strategy's symbol ──
            String symbol = resolveSymbol(agent.getStrategyId());
            if (symbol != null) {
                Map<String, Object> varCheck = riskEngineService.calculateVaR(symbol, 30);
                boolean varBreached = Boolean.TRUE.equals(varCheck.get("breaches"));
                if (varBreached) {
                    log.warn("Agent {} tick ABORTED: VaR breach for symbol={}. VaR95={}%, maxDrawdown={}%",
                            agent.getId(), symbol, varCheck.get("var95"), varCheck.get("maxDrawdown"));
                    statusBroadcast.broadcastAgentStatus(agent.getId(), personaName,
                            role, "ALERT", "VaR breach for " + symbol + " — tick aborted", null);
                    return;
                }
            }

            // ── Execute strategy ──
            log.info("Agent {} executing strategy {}", agent.getId(), agent.getStrategyId());
            ExecutionResponse result = strategyService.executeStrategy(agent.getStrategyId());
            log.info("Agent {} result: action={}, success={}",
                    agent.getId(),
                    result.getAction(),
                    result.isSuccess());

            // Update execution stats
            agent.setTotalExecutions(agent.getTotalExecutions() + 1);
            if (result.isSuccess()) {
                agent.setSuccessfulExecutions(agent.getSuccessfulExecutions() + 1);
            }
            agent.setLastRunAt(Instant.now());
            agent.setUpdatedAt(Instant.now());
            agentRepository.save(agent);

            // Broadcast: agent is idle again
            statusBroadcast.broadcastAgentStatus(agent.getId(), personaName,
                    role, "IDLE", "Completed: " + result.getAction(), agent.getLastConfidence());
        } catch (Exception e) {
            log.error("Agent {} execution failed: {}", agent.getId(), e.getMessage(), e);
            statusBroadcast.broadcastAgentStatus(agent.getId(), personaName,
                    role, "ALERT", "Execution error: " + e.getMessage(), null);
        }
    }

    /**
     * Resolve the symbol for a strategy ID by looking it up in the repository.
     */
    private String resolveSymbol(Long strategyId) {
        return strategyRepository.findById(strategyId)
                .map(Strategy::getSymbol)
                .orElse(null);
    }

    /**
     * Get the default system prompt for a given agent role.
     */
    private String getDefaultPromptForRole(AgentRole role) {
        return switch (role) {
            case QUANT_RESEARCHER      -> AgentSystemPrompts.QUANT_RESEARCHER;
            case BIAS_AUDITOR          -> AgentSystemPrompts.BIAS_AUDITOR;
            case RISK_ANALYST          -> AgentSystemPrompts.RISK_ANALYST;
            case PORTFOLIO_CONSTRUCTOR -> AgentSystemPrompts.PORTFOLIO_CONSTRUCTOR;
            case PSYCHOLOGY_ENFORCER   -> AgentSystemPrompts.PSYCHOLOGY_ENFORCER;
            case PERFORMANCE_ATTRIBUTOR -> AgentSystemPrompts.PERFORMANCE_ATTRIBUTOR;
            case MARKET_REGIME_ANALYST -> AgentSystemPrompts.MARKET_REGIME_ANALYST;
            case EXECUTION_OPTIMIZER    -> AgentSystemPrompts.EXECUTION_OPTIMIZER;
            case HFT_SYSTEMS_ENGINEER  -> AgentSystemPrompts.HFT_SYSTEMS_ENGINEER;
            case EXECUTION_MONITOR     -> AgentSystemPrompts.EXECUTION_MONITOR;
        };
    }

    private TradingAgent findOrThrow(Long id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: id=" + id));
    }

    private TradingAgentResponse toResponse(TradingAgent a) {
        String promptPreview = null;
        if (a.getSystemPrompt() != null) {
            promptPreview = a.getSystemPrompt().length() > SYSTEM_PROMPT_PREVIEW_LENGTH
                    ? a.getSystemPrompt().substring(0, SYSTEM_PROMPT_PREVIEW_LENGTH) + "..."
                    : a.getSystemPrompt();
        }

        return TradingAgentResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .strategyId(a.getStrategyId())
                .cronExpression(a.getCronExpression())
                .active(a.getActive())
                .lastRunAt(a.getLastRunAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .agentRole(a.getAgentRole())
                .systemPromptPreview(promptPreview)
                .lastReasoning(a.getLastReasoning())
                .lastConfidence(a.getLastConfidence())
                .totalExecutions(a.getTotalExecutions())
                .successfulExecutions(a.getSuccessfulExecutions())
                .lifecycleState(a.getLifecycleState())
                .personaName(a.getPersonaName())
                .personaColor(a.getPersonaColor())
                .personaInitials(a.getPersonaInitials())
                .build();
    }
}
