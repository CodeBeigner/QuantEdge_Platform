package com.QuantPlatformApplication.QuantPlatformApplication.controller;

import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentRequest;
import com.QuantPlatformApplication.QuantPlatformApplication.model.dto.TradingAgentResponse;
import com.QuantPlatformApplication.QuantPlatformApplication.service.AgentSchedulerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for trading agent CRUD + start/stop.

 * POST /api/v1/agents — Create an agent
 * GET /api/v1/agents — List all agents
 * GET /api/v1/agents/{id} — Get by ID
 * DELETE /api/v1/agents/{id} — Delete
 * POST /api/v1/agents/{id}/start — Start scheduled execution
 * POST /api/v1/agents/{id}/stop — Stop scheduled execution
 */
@RestController
@RequestMapping("/api/v1/agents")
public class TradingAgentController {

    private final AgentSchedulerService agentService;

    public TradingAgentController(AgentSchedulerService agentService) {
        this.agentService = agentService;
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
}
