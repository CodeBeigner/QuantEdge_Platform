package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Multi-agent consensus mechanism for high-impact trading decisions.
 * Polls multiple agents in parallel and requires majority agreement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConsensusService {

    private final ClaudeAgentService claudeAgent;
    private final ObjectMapper objectMapper;

    private static final int CONSENSUS_TIMEOUT_SECONDS = 60;
    private static final double REQUIRED_AGREEMENT_RATIO = 0.67;

    /**
     * Poll multiple agents for consensus on a trading decision.
     * Returns combined result with individual votes and consensus outcome.
     */
    public Map<String, Object> seekConsensus(String context, AgentRole... roles) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> votes = new ArrayList<>();
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            
            for (AgentRole role : roles) {
                futures.add(executor.submit(() -> {
                    try {
                        Map<String, Object> agentResult = claudeAgent.runAgent(role, context);
                        agentResult.put("voter", role.name());
                        return agentResult;
                    } catch (Exception e) {
                        return Map.of("voter", role.name(), "error", e.getMessage(), "vote", "ABSTAIN");
                    }
                }));
            }

            for (Future<Map<String, Object>> future : futures) {
                try {
                    votes.add(future.get(CONSENSUS_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (TimeoutException e) {
                    votes.add(Map.of("voter", "TIMEOUT", "vote", "ABSTAIN"));
                } catch (Exception e) {
                    votes.add(Map.of("voter", "ERROR", "vote", "ABSTAIN", "error", e.getMessage()));
                }
            }
        }

        // Tally votes
        long approvals = votes.stream()
                .filter(v -> "APPROVE".equals(v.get("risk_decision")) || 
                             "DEPLOY".equals(v.get("decision")) ||
                             "PASS".equals(v.get("psychology_check")))
                .count();
        
        long total = votes.stream()
                .filter(v -> !"ABSTAIN".equals(v.get("vote")))
                .count();

        boolean consensusReached = total > 0 && (double) approvals / total >= REQUIRED_AGREEMENT_RATIO;

        result.put("votes", votes);
        result.put("approvals", approvals);
        result.put("total_votes", total);
        result.put("agreement_ratio", total > 0 ? Math.round((double) approvals / total * 100) / 100.0 : 0);
        result.put("consensus_reached", consensusReached);
        result.put("consensus_decision", consensusReached ? "APPROVED" : "REJECTED");
        result.put("required_ratio", REQUIRED_AGREEMENT_RATIO);

        log.info("Consensus result: {}/{} approved ({}%). Decision: {}", 
                approvals, total, total > 0 ? approvals * 100 / total : 0,
                result.get("consensus_decision"));

        return result;
    }

    /**
     * Pre-trade consensus check: polls Risk Analyst, Psychology Enforcer, and Execution Optimizer.
     */
    public Map<String, Object> preTradeConsensus(String tradeContext) {
        return seekConsensus(tradeContext, 
                AgentRole.RISK_ANALYST, 
                AgentRole.PSYCHOLOGY_ENFORCER, 
                AgentRole.EXECUTION_OPTIMIZER);
    }
}
