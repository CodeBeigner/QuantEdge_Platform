package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.ExecutionResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent;
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
 * Manages trading agent lifecycle: CRUD + cron-based cxecution scheduling.
 * Active agents execute their linked strategy on the configured cron.
 */
@Slf4j
@Service
public class AgentSchedulerService {

    private final TradingAgentRepository agentRepository;
    private final StrategyService strategyService;
    private final TaskScheduler taskScheduler;

    /** Tracks running agent futures for start/stop control */
    private final Map<Long, ScheduledFuture<?>> runningAgents = new ConcurrentHashMap<>();

    public AgentSchedulerService(TradingAgentRepository agentRepository,
            StrategyService strategyService,
            TaskScheduler taskScheduler) {
        this.agentRepository = agentRepository;
        this.strategyService = strategyService;
        this.taskScheduler = taskScheduler;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────

    public TradingAgentResponse createAgent(TradingAgentRequest request) {
        TradingAgent agent = TradingAgent.builder()
                .name(request.getName())
                .strategyId(request.getStrategyId())
                .cronExpression(request.getCronExpression())
                .build();

        TradingAgent saved = agentRepository.save(agent);
        log.info("Created agent: id={}, name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    public List<TradingAgentResponse> getAllAgents() {
        return agentRepository.findAll().stream().map(this::toResponse).toList();
    }

    public TradingAgentResponse getAgent(Long id) {
        return toResponse(findOrThrow(id));
    }

    public void deleteAgent(Long id) {
        stopAgent(id);
        agentRepository.delete(findOrThrow(id));
        log.info("Deleted agent: id={}", id);
    }

    // ── Start / Stop ────────────────────────────────────────────────────

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

    private void executeAgentTick(TradingAgent agent) {
        try {
            log.info("Agent {} executing strategy {}", agent.getId(), agent.getStrategyId());
            ExecutionResponse result = strategyService.executeStrategy(agent.getStrategyId());
            log.info("Agent {} result: action={}, success={}",
                    agent.getId(),
                    result.getAction(),
                    result.isSuccess());

            agent.setLastRunAt(Instant.now());
            agent.setUpdatedAt(Instant.now());
            agentRepository.save(agent);
        } catch (Exception e) {
            log.error("Agent {} execution failed: {}", agent.getId(), e.getMessage(), e);
        }
    }

    private TradingAgent findOrThrow(Long id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: id=" + id));
    }

    private TradingAgentResponse toResponse(TradingAgent a) {
        return TradingAgentResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .strategyId(a.getStrategyId())
                .cronExpression(a.getCronExpression())
                .active(a.getActive())
                .lastRunAt(a.getLastRunAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
