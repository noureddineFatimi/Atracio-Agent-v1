package atracio.agent.agent;

import atracio.agent.dto.ChatRequest;
import atracio.agent.dto.ChatResponse;
import atracio.agent.dto.ToolCallDto;
import atracio.agent.provider.LlmProvider;
import atracio.agent.provider.LlmProvider.LlmResponse;
import atracio.agent.provider.LlmProvider.ToolCall;
import atracio.agent.tools.ToolDispatcher;
import atracio.agent.tools.ToolDefinitionRegistry;               
import atracio.agent.tools.ToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
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

        LlmResponse llmResponse;

        List<ToolCallDto> toolCallDtos = new ArrayList<>();

        // 4a. LLM requested a tool call
        while (true) {
            // 3. First LLM call
            llmResponse = llmProvider.chat(systemPrompt, history, tools);
            if (!llmResponse.hasToolCalls()) {
                log.info("AgentOrchestrator: [{}]  no tool calls requested", conversationId);
                break;
            }

            List<ToolCall> toolCalls = llmResponse.getToolCalls();
            log.info("AgentOrchestrator: [{}] tool calls requested='{}'",
                    conversationId, toolCalls);

            // Record the assistant's tool call decision in history
            conversationService.addAssistantToolCalls(
                    conversationId,
                    toolCalls
            ); 

            for (ToolCall toolCall : toolCalls) {
                ToolResponse toolResult = toolDispatcher.dispatch(toolCall, tenant, bearerToken);
                // Execute the tool
            
                log.info("AgentOrchestrator: [{}] tool='{}' ok={}",
                    conversationId, toolCall.getName(), toolResult.isOk());

                // Add tool result to history
                conversationService.addToolResult(
                        conversationId,
                        toolCall.getId(),
                        toolCall.getName(),
                        toolResult
                );

                log.info("AgentOrchestrator: [{}] tool='{}' success={} tool_response_data={}",
                    conversationId, toolCall.getName(), toolResult.isOk() ,toolResult.getData());

                toolCallDtos.add(new ToolCallDto(toolResult.getTool(), toolResult.isOk() == true ? "success" : "failed"));
            }
            // Second LLM call — produce natural language reply from tool result
            history = conversationService.getHistory(conversationId);
        }

            String assistantMessage = llmResponse.getText() != null
                    ? llmResponse.getText()
                    : fallbackMessage();

            conversationService.addAssistantMessage(conversationId, assistantMessage);

            log.info("AgentOrchestrator: [{}] reply with the response = \"{}\" ", conversationId, assistantMessage);
            return new ChatResponse(
                    assistantMessage,
                    conversationId,
                    toolCallDtos.size() == 0 ? null : toolCallDtos
            );
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
    private String fallbackMessage() {
        return "Empty response";
    }
}