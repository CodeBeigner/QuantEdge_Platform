package com.QuantPlatformApplication.QuantPlatformApplication.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.QuantPlatformApplication.QuantPlatformApplication.engine.agent.AgentSystemPrompts;
import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.AgentRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service that wraps the Anthropic Claude API for AI agent execution.
 *
 * <p>
 * Provides a typed interface for running agents by role (with pre-defined
 * system prompts from {@link AgentSystemPrompts}) or with custom prompts.
 * All responses are parsed as JSON Maps.
 *
 * <p>
 * Uses {@code claude-opus-4-6} model with 2048 max output tokens.
 *
 * @see AgentSystemPrompts
 * @see AgentRole
 */
@Slf4j
@Service
public class ClaudeAgentService {

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private static final String MODEL = "claude-opus-4-6";
    private static final int MAX_TOKENS = 2048;

    /**
     * Construct the ClaudeAgentService with API key and ObjectMapper.
     *
     * @param apiKey       Anthropic API key from configuration
     * @param objectMapper Jackson ObjectMapper for JSON parsing
     */
    public ClaudeAgentService(
            @Value("${anthropic.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Run a specific agent role against a context payload.
     *
     * <p>
     * Uses the pre-defined system prompt for the given role from
     * {@link AgentSystemPrompts}. The context JSON is sent as the user message.
     *
     * @param role        the AI agent role to execute
     * @param contextJson JSON string with context data for the agent
     * @return parsed JSON response as a Map, or an error map if execution fails
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> runAgent(AgentRole role, String contextJson) {
        String systemPrompt = getSystemPrompt(role);

        try {
            Message response = client.messages().create(
                    MessageCreateParams.builder()
                            .model(MODEL)
                            .maxTokens(MAX_TOKENS)
                            .system(systemPrompt)
                            .addUserMessage(contextJson)
                            .build());

            String content = extractTextContent(response);
            // Strip markdown fences if present
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            log.info("Agent {} completed. Tokens used: input={}, output={}",
                    role,
                    response.usage().inputTokens(),
                    response.usage().outputTokens());

            return objectMapper.readValue(content, Map.class);

        } catch (Exception e) {
            log.error("Claude agent {} failed: {}", role, e.getMessage(), e);
            return Map.of(
                    "error", e.getMessage(),
                    "agent_role", role.name(),
                    "status", "FAILED");
        }
    }

    /**
     * Run an agent with a custom system prompt (for user-configured agents).
     *
     * @param systemPrompt custom system prompt text
     * @param contextJson  JSON string with context data
     * @return parsed JSON response as a Map, or an error map if execution fails
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> runAgentWithCustomPrompt(String systemPrompt, String contextJson) {
        try {
            Message response = client.messages().create(
                    MessageCreateParams.builder()
                            .model(MODEL)
                            .maxTokens(MAX_TOKENS)
                            .system(systemPrompt)
                            .addUserMessage(contextJson)
                            .build());

            String content = extractTextContent(response);
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return objectMapper.readValue(content, Map.class);

        } catch (Exception e) {
            log.error("Custom agent failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage(), "status", "FAILED");
        }
    }

    /**
     * Extract text content from the Claude API response.
     * Handles the content block structure of the Anthropic SDK.
     *
     * @param response the Message response from Claude
     * @return extracted text content
     */
    private String extractTextContent(Message response) {
        return response.content().stream()
                .filter(ContentBlock::isText)
                .map(block -> block.asText().text())
                .findFirst()
                .orElse("{}");
    }

    /**
     * Map an AgentRole to its corresponding system prompt.
     *
     * @param role the agent role
     * @return the system prompt string for that role
     */
    private String getSystemPrompt(AgentRole role) {
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
}
