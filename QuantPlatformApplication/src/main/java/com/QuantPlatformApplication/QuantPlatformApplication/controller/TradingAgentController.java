package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.engine.agent.AgentSystemPrompts;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentConversation;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.TradingAgentRepository;
import com.QuantPlatformApplication.QuantPlatformApplication.service.AgentConversationService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.AgentPipelineService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.AgentSchedulerService;
import com.QuantPlatformApplication.QuantPlatformApplication.service.ClaudeAgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for trading agent CRUD, start/stop, AI pipeline execution,
 * agent chat, and CEO command processing.
 *
 * <ul>
 *   <li>POST   /api/v1/agents                  — Create an agent</li>
 *   <li>GET    /api/v1/agents                  — List all agents</li>
 *   <li>GET    /api/v1/agents/{id}             — Get by ID</li>
 *   <li>DELETE /api/v1/agents/{id}             — Delete</li>
 *   <li>POST   /api/v1/agents/{id}/start       — Start scheduled execution</li>
 *   <li>POST   /api/v1/agents/{id}/stop        — Stop scheduled execution</li>
 *   <li>POST   /api/v1/agents/{id}/run-pipeline — Run AI research pipeline</li>
 *   <li>POST   /api/v1/agents/{id}/attribution  — Run attribution pipeline</li>
 *   <li>POST   /api/v1/agents/{id}/chat         — Chat with a specific agent</li>
 *   <li>GET    /api/v1/agents/{id}/conversation  — Get conversation history</li>
 *   <li>DELETE /api/v1/agents/{id}/conversation  — Clear conversation</li>
 *   <li>POST   /api/v1/agents/ceo-command        — CEO broadcast command</li>
 *   <li>GET    /api/v1/agents/roles              — List all available agent roles</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agents")
public class TradingAgentController {

    private final AgentSchedulerService agentService;
    private final AgentPipelineService agentPipelineService;
    private final AgentConversationService conversationService;
    private final ClaudeAgentService claudeService;
    private final TradingAgentRepository agentRepository;

    public TradingAgentController(AgentSchedulerService agentService,
            AgentPipelineService agentPipelineService,
            AgentConversationService conversationService,
            ClaudeAgentService claudeService,
            TradingAgentRepository agentRepository) {
        this.agentService = agentService;
        this.agentPipelineService = agentPipelineService;
        this.conversationService = conversationService;
        this.claudeService = claudeService;
        this.agentRepository = agentRepository;
    }

    // ── CRUD ────────────────────────────────────────────────────────────

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

    // ── Start / Stop ────────────────────────────────────────────────────

    @PostMapping("/{id}/start")
    public ResponseEntity<TradingAgentResponse> start(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.startAgent(id));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<TradingAgentResponse> stop(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.stopAgent(id));
    }

    // ── Pipelines ───────────────────────────────────────────────────────

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
     *
     * @param symbol trading symbol for context (defaults to SPY)
     * @return system audit result
     */
    @PostMapping("/system-audit")
    public ResponseEntity<Map<String, Object>> runSystemAudit(
            @RequestParam(defaultValue = "SPY") String symbol) {
        return ResponseEntity.ok(agentPipelineService.runSystemAuditPipeline(symbol));
    }

    /**
     * Run the Execution Monitor pipeline.
     *
     * @param symbol trading symbol for context (defaults to SPY)
     * @return monitoring result
     */
    @PostMapping("/execution-monitor")
    public ResponseEntity<Map<String, Object>> runExecutionMonitor(
            @RequestParam(defaultValue = "SPY") String symbol) {
        return ResponseEntity.ok(agentPipelineService.runExecutionMonitorPipeline(symbol));
    }

    // ── Agent Chat ──────────────────────────────────────────────────────

    /**
     * Have a natural-language conversation with a specific agent.
     *
     * @param id   agent ID
     * @param body request body with "message" field
     * @return agent's conversational reply
     */
    @PostMapping("/{id}/chat")
    public ResponseEntity<Map<String, Object>> chatWithAgent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message is required"));
        }
        return ResponseEntity.ok(conversationService.chat(id, message));
    }

    /**
     * Get conversation history for a specific agent.
     *
     * @param id agent ID
     * @return list of conversation messages
     */
    @GetMapping("/{id}/conversation")
    public ResponseEntity<List<Map<String, Object>>> getConversation(@PathVariable Long id) {
        List<AgentConversation> history = conversationService.getHistory(id);
        List<Map<String, Object>> result = history.stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("agentId", c.getAgentId());
                    m.put("role", c.getRole());
                    m.put("content", c.getContent());
                    m.put("createdAt", c.getCreatedAt().toString());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Clear conversation history for a specific agent.
     *
     * @param id agent ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}/conversation")
    public ResponseEntity<Void> clearConversation(@PathVariable Long id) {
        conversationService.clearHistory(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Process a CEO broadcast command — routes to the most relevant agent.
     *
     * @param body request body with "message" field
     * @return the responding agent's name, role, and reply
     */
    @PostMapping("/ceo-command")
    public ResponseEntity<Map<String, Object>> ceoCommand(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message is required"));
        }

        List<TradingAgent> agents = agentRepository.findAll();
        Map<String, Object> firmContext = Map.of("agent_count", agents.size());

        return ResponseEntity.ok(claudeService.processCeoCommand(message, agents, firmContext));
    }

    // ── Roles ───────────────────────────────────────────────────────────

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

