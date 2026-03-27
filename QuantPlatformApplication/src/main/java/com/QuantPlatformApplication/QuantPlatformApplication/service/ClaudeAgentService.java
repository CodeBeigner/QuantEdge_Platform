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

    // ── Conversation Mode ────────────────────────────────────────────

    /**
     * Have a natural-language conversation with a specific agent.
     * Uses the agent's system prompt but instructs conversational output (not JSON).
     * Maintains conversation history for context continuity.
     *
     * @param agentId             the agent's database ID
     * @param userMessage         the CEO's message to this agent
     * @param conversationHistory list of prior messages [{role, content}, ...]
     * @param agentSystemPrompt   the agent's full system prompt
     * @param personaName         the agent's name (e.g. "Marcus K.")
     * @return the agent's conversational reply as a plain string
     */
    public String chatWithAgent(Long agentId, String userMessage,
            java.util.List<java.util.Map<String, String>> conversationHistory,
            String agentSystemPrompt, String personaName) {

        String conversationalSystem = agentSystemPrompt + "\n\n"
                + "CONVERSATION MODE — OVERRIDE JSON REQUIREMENT:\n"
                + "You are now in a direct conversation with your CEO.\n"
                + "Respond in natural, professional English — NOT JSON.\n"
                + "Keep responses concise (2-4 paragraphs max).\n"
                + "Address the CEO directly. Use \"I\" and refer to your analysis.\n"
                + "Sign off with your name: " + personaName + "\n"
                + "Do not include JSON blobs. Speak like a real professional colleague.\n";

        try {
            var paramsBuilder = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(MAX_TOKENS)
                    .system(conversationalSystem);

            // Add conversation history
            for (java.util.Map<String, String> msg : conversationHistory) {
                if ("user".equals(msg.get("role"))) {
                    paramsBuilder.addUserMessage(msg.get("content"));
                } else {
                    paramsBuilder.addAssistantMessage(msg.get("content"));
                }
            }
            paramsBuilder.addUserMessage(userMessage);

            Message response = client.messages().create(paramsBuilder.build());
            String reply = extractTextContent(response);

            log.info("Chat with agent {} ({}). Tokens: input={}, output={}",
                    agentId, personaName,
                    response.usage().inputTokens(),
                    response.usage().outputTokens());

            return reply;
        } catch (Exception e) {
            log.error("Chat with agent {} failed: {}", agentId, e.getMessage(), e);
            return "I'm having trouble responding right now. Please try again shortly. — " + personaName;
        }
    }

    /**
     * Process a CEO-level message that gets routed to the most relevant agent.
     * Returns a synthesized response from the best-matched agent.
     *
     * @param message      the CEO's broadcast message
     * @param activeAgents list of currently active trading agents
     * @param firmContext  map of firm-level context (name, type, capital, etc.)
     * @return response map with agent_name, agent_role, response, confidence
     */
    public Map<String, Object> processCeoCommand(String message,
            java.util.List<com.QuantPlatformApplication.QuantPlatformApplication.model.entity.TradingAgent> activeAgents,
            Map<String, Object> firmContext) {

        if (activeAgents.isEmpty()) {
            return Map.of(
                    "agent_name", "System",
                    "agent_role", "SYSTEM",
                    "response", "No agents are currently available. Please set up your firm first.",
                    "confidence", 0.0
            );
        }

        // Build a routing prompt to determine which agent should respond
        StringBuilder agentList = new StringBuilder();
        for (var agent : activeAgents) {
            String name = agent.getPersonaName() != null ? agent.getPersonaName() : agent.getName();
            agentList.append("- ").append(name)
                    .append(" (").append(agent.getAgentRole()).append(")\n");
        }

        String routingSystemPrompt = "You are the AI routing system for a trading firm. "
                + "The CEO has sent a message. Based on the message content, respond as the most "
                + "relevant team member. Available team members:\n"
                + agentList
                + "\nFirm context: " + firmContext
                + "\nRespond in first person as that team member. "
                + "Keep response to 2-3 paragraphs. Sign off with the team member's name.";

        try {
            Message response = client.messages().create(
                    MessageCreateParams.builder()
                            .model(MODEL)
                            .maxTokens(MAX_TOKENS)
                            .system(routingSystemPrompt)
                            .addUserMessage(message)
                            .build());

            String reply = extractTextContent(response);

            // Try to identify which agent responded from the sign-off
            String respondingAgentName = activeAgents.get(0).getPersonaName() != null
                    ? activeAgents.get(0).getPersonaName()
                    : activeAgents.get(0).getName();
            String respondingRole = activeAgents.get(0).getAgentRole().name();

            for (var agent : activeAgents) {
                String name = agent.getPersonaName() != null ? agent.getPersonaName() : agent.getName();
                if (reply.contains(name)) {
                    respondingAgentName = name;
                    respondingRole = agent.getAgentRole().name();
                    break;
                }
            }

            return Map.of(
                    "agent_name", respondingAgentName,
                    "agent_role", respondingRole,
                    "response", reply,
                    "confidence", 0.85
            );
        } catch (Exception e) {
            log.error("CEO command processing failed: {}", e.getMessage(), e);
            return Map.of(
                    "agent_name", "System",
                    "agent_role", "SYSTEM",
                    "response", "I'm having difficulty processing your request right now. Please try again.",
                    "confidence", 0.0
            );
        }
    }

    // ── Firm Context Injection ───────────────────────────────────────

    /**
     * Run a specific agent role with firm context injected into the system prompt.
     *
     * @param role        the AI agent role to execute
     * @param contextJson JSON string with context data for the agent
     * @param firm        the firm profile for context injection (may be null)
     * @return parsed JSON response as a Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> runAgent(AgentRole role, String contextJson,
            com.QuantPlatformApplication.QuantPlatformApplication.model.entity.FirmProfile firm) {
        String systemPrompt = injectFirmContext(getSystemPrompt(role), firm);

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

            log.info("Agent {} (firm-aware) completed. Tokens: input={}, output={}",
                    role, response.usage().inputTokens(), response.usage().outputTokens());

            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            log.error("Claude agent {} (firm-aware) failed: {}", role, e.getMessage(), e);
            return Map.of("error", e.getMessage(), "agent_role", role.name(), "status", "FAILED");
        }
    }

    /**
     * Inject firm context into an agent's system prompt.
     *
     * @param systemPrompt the original system prompt
     * @param firm         the firm profile (may be null)
     * @return the augmented system prompt
     */
    private String injectFirmContext(String systemPrompt,
            com.QuantPlatformApplication.QuantPlatformApplication.model.entity.FirmProfile firm) {
        if (firm == null) return systemPrompt;
        return String.format(
                "FIRM CONTEXT:\n"
                + "You work at %s, a %s firm.\n"
                + "Initial Capital: $%s\n"
                + "Risk Appetite: %s\n"
                + "Your decisions must align with this firm's mandate and risk profile.\n\n",
                firm.getFirmName(),
                firm.getFirmType().name().replace("_", " "),
                firm.getInitialCapital().toPlainString(),
                firm.getRiskAppetite())
                + systemPrompt;
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
