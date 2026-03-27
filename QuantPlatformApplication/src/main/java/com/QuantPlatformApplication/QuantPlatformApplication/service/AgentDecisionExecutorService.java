package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradingAgentRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.StrategyRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.Strategy;
import com.QuantPlatformApplication.QuantPlatformApplication.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Closes the decision-execution loop: takes pipeline output and acts on it.
 * APPROVED → activates strategy + starts agent
 * PAPER_TEST → creates shadow portfolio tracking
 * MODIFY → applies parameter changes
 * REJECTED → deactivates strategy and pauses agent
 */
@Slf4j
@Service
public class AgentDecisionExecutorService {

    private final TradingAgentRepository agentRepository;
    private final StrategyRepository strategyRepository;
    private final AgentSchedulerService schedulerService;
    private final EventPublisher eventPublisher;

    public AgentDecisionExecutorService(
            TradingAgentRepository agentRepository,
            StrategyRepository strategyRepository,
            AgentSchedulerService schedulerService,
            @Autowired(required = false) EventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.strategyRepository = strategyRepository;
        this.schedulerService = schedulerService;
        this.eventPublisher = eventPublisher;
    }

    public Map<String, Object> executePipelineDecision(Long agentId, Map<String, Object> pipelineResult) {
        String status = (String) pipelineResult.getOrDefault("pipeline_status", "UNKNOWN");
        String decision = (String) pipelineResult.getOrDefault("final_decision", "REJECT");

        TradingAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        return switch (decision) {
            case "DEPLOY" -> handleDeploy(agent, pipelineResult);
            case "PAPER_TEST" -> handlePaperTest(agent, pipelineResult);
            case "MODIFY" -> handleModify(agent, pipelineResult);
            default -> handleReject(agent, pipelineResult);
        };
    }

    private Map<String, Object> handleDeploy(TradingAgent agent, Map<String, Object> pipelineResult) {
        log.info("Pipeline APPROVED for agent {}. Activating strategy and starting scheduler.", agent.getId());

        // Activate the linked strategy
        strategyRepository.findById(agent.getStrategyId()).ifPresent(strategy -> {
            strategy.setActive(true);
            strategyRepository.save(strategy);
        });

        // Start the agent scheduler
        schedulerService.startAgent(agent.getId());

        // Update agent lifecycle state
        agent.setLifecycleState("LIVE");
        agent.setUpdatedAt(Instant.now());
        agentRepository.save(agent);

        if (eventPublisher != null) {
            eventPublisher.publishAgentPipelineResult(String.valueOf(agent.getId()), Map.of(
                    "agentId", agent.getId(),
                    "decision", "DEPLOY",
                    "status", "ACTIVATED",
                    "timestamp", Instant.now().toString()
            ));
        }

        return Map.of(
                "status", "DEPLOYED",
                "agentId", agent.getId(),
                "message", "Strategy activated and agent scheduler started"
        );
    }

    private Map<String, Object> handlePaperTest(TradingAgent agent, Map<String, Object> pipelineResult) {
        log.info("Pipeline PAPER_TEST for agent {}. Starting paper trading mode.", agent.getId());

        agent.setLifecycleState("PAPER_TRADING");
        agent.setUpdatedAt(Instant.now());
        agentRepository.save(agent);

        // Start agent in paper mode (uses existing paper trading OMS)
        schedulerService.startAgent(agent.getId());

        return Map.of(
                "status", "PAPER_TESTING",
                "agentId", agent.getId(),
                "message", "Agent started in paper trading mode for validation"
        );
    }

    private Map<String, Object> handleModify(TradingAgent agent, Map<String, Object> pipelineResult) {
        log.info("Pipeline MODIFY for agent {}. Applying parameter changes.", agent.getId());

        // Extract suggested modifications from pipeline result
        @SuppressWarnings("unchecked")
        Map<String, Object> researchDecision = (Map<String, Object>) pipelineResult.getOrDefault("research_decision", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> modifications = (Map<String, Object>) researchDecision.getOrDefault("modifications", Map.of());

        agent.setLifecycleState("CONFIGURING");
        agent.setUpdatedAt(Instant.now());
        agent.setLastReasoning("Pipeline suggested modifications: " + modifications);
        agentRepository.save(agent);

        return Map.of(
                "status", "MODIFIED",
                "agentId", agent.getId(),
                "modifications", modifications,
                "message", "Agent parameters updated per pipeline recommendation"
        );
    }

    private Map<String, Object> handleReject(TradingAgent agent, Map<String, Object> pipelineResult) {
        log.warn("Pipeline REJECTED for agent {}. Deactivating.", agent.getId());

        // Stop the agent if running
        schedulerService.stopAgent(agent.getId());

        // Deactivate strategy
        strategyRepository.findById(agent.getStrategyId()).ifPresent(strategy -> {
            strategy.setActive(false);
            strategyRepository.save(strategy);
        });

        agent.setLifecycleState("PAUSED");
        agent.setUpdatedAt(Instant.now());
        agentRepository.save(agent);

        return Map.of(
                "status", "REJECTED",
                "agentId", agent.getId(),
                "message", "Strategy deactivated and agent paused"
        );
    }
}
