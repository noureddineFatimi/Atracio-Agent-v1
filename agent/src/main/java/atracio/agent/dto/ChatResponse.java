package atracio.agent.dto;

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

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public ChatResponse() {}

    public ChatResponse(String assistantMessage, String conversationId,
                         List<ToolCallDto> toolCalls) {
        this.assistantMessage = assistantMessage;
        this.conversationId   = conversationId;
        this.toolCalls         = toolCalls;
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
}