package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.agent.AgentSystemPrompts;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import com.QuantPlatformApplication.QuantPlatformApplication.service.AgentPipelineService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.AgentSchedulerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST controller for trading agent CRUD, start/stop, and AI pipeline execution.
 *
 * <ul>
 *   <li>POST   /api/v1/agents             — Create an agent</li>
 *   <li>GET    /api/v1/agents             — List all agents</li>
 *   <li>GET    /api/v1/agents/{id}        — Get by ID</li>
 *   <li>DELETE /api/v1/agents/{id}        — Delete</li>
 *   <li>POST   /api/v1/agents/{id}/start  — Start scheduled execution</li>
 *   <li>POST   /api/v1/agents/{id}/stop   — Stop scheduled execution</li>
 *   <li>POST   /api/v1/agents/{id}/run-pipeline  — Run AI research pipeline</li>
 *   <li>POST   /api/v1/agents/{id}/attribution   — Run attribution pipeline</li>
 *   <li>GET    /api/v1/agents/roles       — List all available agent roles</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agents")
public class TradingAgentController {

    private final AgentSchedulerService agentService;
    private final AgentPipelineService agentPipelineService;

    public TradingAgentController(AgentSchedulerService agentService,
            AgentPipelineService agentPipelineService) {
        this.agentService = agentService;
        this.agentPipelineService = agentPipelineService;
    }

    @PostMapping
    public ResponseEntity<TradingAgentResponse> create(@Valid @RequestBody TradingAgentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.createAgent(request));
    }

    @GetMapping
    public ResponseEntity<List<TradingAgentResponse>> listAll() {
        return ResponseEntity.ok(agentService.getAllAgents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TradingAgentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.getAgent(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<TradingAgentResponse> start(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.startAgent(id));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<TradingAgentResponse> stop(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.stopAgent(id));
    }

    /**
     * Run the full AI research pipeline for an agent's strategy.
     *
     * @param id     agent ID
     * @param symbol trading symbol (defaults to SPY)
     * @return pipeline result with all agent outputs
     */
    @PostMapping("/{id}/run-pipeline")
    public ResponseEntity<Map<String, Object>> runPipeline(
            @PathVariable Long id,
            @RequestParam(defaultValue = "SPY") String symbol) {
        return ResponseEntity.ok(agentPipelineService.runResearchPipeline(id, symbol));
    }

    /**
     * Run the attribution pipeline for an agent's strategy.
     *
     * @param id     agent ID
     * @param symbol trading symbol (defaults to SPY)
     * @return attribution result
     */
    @PostMapping("/{id}/attribution")
    public ResponseEntity<Map<String, Object>> runAttribution(
            @PathVariable Long id,
            @RequestParam(defaultValue = "SPY") String symbol) {
        return ResponseEntity.ok(agentPipelineService.runAttributionPipeline(id, symbol));
    }

    /**
     * Run the HFT Systems Engineer audit pipeline.
     * Analyzes the platform as a principal engineer at a top HFT firm would.
     *
     * @param symbol trading symbol for context (defaults to SPY)
     * @return system audit result with optimizations and production readiness assessment
     */
    @PostMapping("/system-audit")
    public ResponseEntity<Map<String, Object>> runSystemAudit(
            @RequestParam(defaultValue = "SPY") String symbol) {
        return ResponseEntity.ok(agentPipelineService.runSystemAuditPipeline(symbol));
    }

    /**
     * Run the Execution Monitor pipeline.
     * Performs real-time surveillance of all active algorithms and trade executions.
     *
     * @param symbol trading symbol for context (defaults to SPY)
     * @return monitoring result with anomalies, circuit breaker status, and exec quality
     */
    @PostMapping("/execution-monitor")
    public ResponseEntity<Map<String, Object>> runExecutionMonitor(
            @RequestParam(defaultValue = "SPY") String symbol) {
        return ResponseEntity.ok(agentPipelineService.runExecutionMonitorPipeline(symbol));
    }

    /**
     * List all available agent roles with their default system prompt previews.
     *
     * @return list of role descriptors
     */
    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> getAvailableRoles() {
        List<Map<String, Object>> roles = Arrays.stream(AgentRole.values())
                .map(role -> {
                    String prompt = getPromptForRole(role);
                    String preview = prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt;
                    return Map.<String, Object>of(
                            "role", role.name(),
                            "promptPreview", preview
                    );
                })
                .toList();
        return ResponseEntity.ok(roles);
    }

    private String getPromptForRole(AgentRole role) {
        return switch (role) {
            case QUANT_RESEARCHER      -> AgentSystemPrompts.QUANT_RESEARCHER;
            case BIAS_AUDITOR          -> AgentSystemPrompts.BIAS_AUDITOR;
            case RISK_ANALYST          -> AgentSystemPrompts.RISK_ANALYST;
            case PORTFOLIO_CONSTRUCTOR -> AgentSystemPrompts.PORTFOLIO_CONSTRUCTOR;
            case PSYCHOLOGY_ENFORCER   -> AgentSystemPrompts.PSYCHOLOGY_ENFORCER;
            case PERFORMANCE_ATTRIBUTOR -> AgentSystemPrompts.PERFORMANCE_ATTRIBUTOR;
            case MARKET_REGIME_ANALYST -> AgentSystemPrompts.MARKET_REGIME_ANALYST;
            case EXECUTION_OPTIMIZER   -> AgentSystemPrompts.EXECUTION_OPTIMIZER;
            case HFT_SYSTEMS_ENGINEER  -> AgentSystemPrompts.HFT_SYSTEMS_ENGINEER;
            case EXECUTION_MONITOR     -> AgentSystemPrompts.EXECUTION_MONITOR;
        };
    }
}
