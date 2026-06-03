package atracio.agent.dto;
import atracio.agent.provider.LlmProvider.ToolCall;

import java.util.List;

/**
 * Outbound response from AgentOrchestrator — returned by ChatController.
 */
public class ChatResponse {

    /** The assistant's natural language reply. */
    private String assistantMessage;

    /** The conversationId echoed back so the client can reuse it on the next turn. */
    private String conversationId;

    /**
     * The name of the tool that was called to produce this response, if any.
     * Null if the LLM answered directly without calling a tool.
     * Status field to know if the tool is executed successfully or not.
     * Useful for the UI to show what action was taken.
     */
    private List<ToolCallDto> toolCalls;

    private boolean requiresTokenRefresh;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public ChatResponse() {}

    public ChatResponse(String assistantMessage, String conversationId,
                         List<ToolCallDto> toolCalls, boolean requiresTokenRefresh ) {
        this.assistantMessage = assistantMessage;
        this.conversationId   = conversationId;
        this.toolCalls         = toolCalls;
        this.requiresTokenRefresh = requiresTokenRefresh;
    }

    /**
     * Token expired — frontend must refresh and retry.
     *
     * assistantMessage is a user-friendly prompt to re-authenticate,
     * shown only if the frontend cannot handle the refresh silently.
     */
    public static ChatResponse tokenExpired(String conversationId, ToolCall toolCall) {
        return new ChatResponse(
                "Your session has expired.",
                conversationId, List.of(new ToolCallDto(toolCall.getName(), "failed")), true);
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String  getAssistantMessage()            { return assistantMessage; }
    public void    setAssistantMessage(String v)    { this.assistantMessage = v; }

    public String  getConversationId()              { return conversationId; }
    public void    setConversationId(String v)      { this.conversationId = v; }

    public List<ToolCallDto>  getToolCalls()                    { return toolCalls; }
    public void    setToolCalls(List<ToolCallDto> v)            { this.toolCalls = v; }

    public boolean isRequiresTokenRefresh()           { return requiresTokenRefresh; }
    public void    setRequiresTokenRefresh(boolean v) { this.requiresTokenRefresh = v; }
}