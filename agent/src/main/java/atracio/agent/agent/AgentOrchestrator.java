package atracio.agent.agent;

import atracio.agent.dto.ChatRequest;
import atracio.agent.dto.ChatResponse;
import atracio.agent.provider.LlmProvider;
import atracio.agent.provider.LlmProvider.LlmResponse;
import atracio.agent.provider.LlmProvider.ToolCall;
import atracio.agent.tools.ToolDispatcher;
import atracio.agent.tools.ToolDefinitionRegistry;
import atracio.agent.tools.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Central orchestrator — wires together the LLM, tool execution, and conversation history.
 *
 * Flow per chat() call:
 *
 *   1. Validate the request
 *   2. Add the user message to history
 *   3. Call the LLM with system prompt + history + tool definitions
 *   4a. If the LLM returns a tool call:
 *       - Execute the tool via ToolDispatcher
 *       - Add the tool call + result to history
 *       - Call the LLM again to produce the final reply
 *   4b. If the LLM returns text directly:
 *       - Add the reply to history and return it
 *   5. Return a ChatResponse to the caller
 *
 * The orchestrator handles one tool call per turn (v1 scope).
 * Multi-step tool chaining is deferred to a future phase.
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final LlmProvider            llmProvider;
    private final SystemPromptFactory    systemPromptFactory;
    private final ConversationService    conversationService;
    private final ToolDefinitionRegistry toolDefinitionRegistry;
    private final ToolDispatcher         toolDispatcher;

    public AgentOrchestrator(LlmProvider llmProvider,
                             SystemPromptFactory systemPromptFactory,
                             ConversationService conversationService,
                             ToolDefinitionRegistry toolDefinitionRegistry,
                             ToolDispatcher toolDispatcher) {
        this.llmProvider            = llmProvider;
        this.systemPromptFactory    = systemPromptFactory;
        this.conversationService    = conversationService;
        this.toolDefinitionRegistry = toolDefinitionRegistry;
        this.toolDispatcher         = toolDispatcher;
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Processes one user turn and returns the agent reply.
     *
     * @param request ChatRequest with userMessage, conversationId, tenant, bearerToken
     * @return ChatResponse with assistantMessage and metadata
     */
    public ChatResponse chat(ChatRequest request) {
        validate(request);

        String conversationId = request.getConversationId();
        String tenant         = request.getTenant();
        String bearerToken    = request.getBearerToken();
        String userMessage    = request.getUserMessage().trim();

        log.info("AgentOrchestrator: [{}] user='{}'", conversationId, userMessage);

        // 1. Add user message to history
        conversationService.addUserMessage(conversationId, userMessage);

        // 2. Build system prompt and get current history
        String                    systemPrompt = systemPromptFactory.build();
        List<Map<String, Object>> history      = conversationService.getHistory(conversationId);
        List<Map<String, Object>> tools        = toolDefinitionRegistry.getAll();
        
        // 3. First LLM call
        LlmResponse llmResponse = llmProvider.chat(systemPrompt, history, tools);

        // 4a. LLM requested a tool call
        if (llmResponse.hasToolCall()) {
            ToolCall toolCall = llmResponse.getToolCall();
            log.info("AgentOrchestrator: [{}] tool call requested='{}'",
                    conversationId, toolCall.getName());

            // Record the assistant's tool call decision in history
            conversationService.addAssistantToolCall(
                    conversationId,
                    toolCall.getId(),
                    toolCall.getName(),
                    argumentsAsString(toolCall.getArguments())
            );

            // Execute the tool
            ToolResponse toolResult = toolDispatcher.dispatch(toolCall, tenant, bearerToken);
            log.info("AgentOrchestrator: [{}] tool='{}' ok={}",
                    conversationId, toolCall.getName(), toolResult.isOk());

            // Add tool result to history
            conversationService.addToolResult(
                    conversationId,
                    toolCall.getId(),
                    toolCall.getName(),
                    toolResult
            );

            // Second LLM call — produce natural language reply from tool result
            List<Map<String, Object>> updatedHistory =
                    conversationService.getHistory(conversationId);

            LlmResponse finalResponse = llmProvider.chat(systemPrompt, updatedHistory, tools);
            String assistantMessage = finalResponse.getText() != null
                    ? finalResponse.getText()
                    : fallbackMessage(toolResult);

            conversationService.addAssistantMessage(conversationId, assistantMessage);

            log.info("AgentOrchestrator: [{}] reply produced after tool call", conversationId);
            return ChatResponse.withTool(
                    assistantMessage,
                    conversationId,
                    toolCall.getName(),
                    toolResult.isOk()
            );
        }

        // 4b. LLM returned a direct text reply
        String assistantMessage = llmResponse.getText();
        conversationService.addAssistantMessage(conversationId, assistantMessage);

        log.info("AgentOrchestrator: [{}] direct reply produced", conversationId);
        return ChatResponse.direct(assistantMessage, conversationId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void validate(ChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ChatRequest must not be null.");
        }
        if (request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            throw new IllegalArgumentException("ChatRequest.userMessage must not be blank.");
        }
        if (request.getConversationId() == null || request.getConversationId().isBlank()) {
            throw new IllegalArgumentException("ChatRequest.conversationId must not be blank.");
        }
        if (request.getBearerToken() == null || request.getBearerToken().isBlank()) {
            throw new IllegalArgumentException("ChatRequest.bearerToken must not be blank.");
        }
    }

    /**
     * Fallback message when the second LLM call returns null text.
     * This should never happen in practice but prevents a NullPointerException.
     */
    private String fallbackMessage(ToolResponse toolResult) {
        if (toolResult.isOk()) {
            return "The operation completed successfully.";
        }
        return "An error occurred: " + toolResult.getError().message();
    }

    /**
     * Converts the tool call arguments map to a compact JSON string for history storage.
     */
    private String argumentsAsString(Map<String, Object> arguments) {
        if (arguments == null) return "{}";
        try {
            StringBuilder sb = new StringBuilder("{");
            arguments.forEach((k, v) -> {
                sb.append("\"").append(k).append("\":");
                if (v instanceof String s) sb.append("\"").append(s).append("\"");
                else sb.append(v);
                sb.append(",");
            });
            if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
            sb.append("}");
            return sb.toString();
        } catch (Exception ex) {
            return "{}";
        }
    }
}