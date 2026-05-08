package atracio.agent.dto;

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
     * Useful for the UI to show what action was taken.
     */
    private String toolUsed;

    /**
     * Whether the tool call succeeded.
     * Null if no tool was called.
     */
    private Boolean toolSuccess;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public ChatResponse() {}

    private ChatResponse(String assistantMessage, String conversationId,
                         String toolUsed, Boolean toolSuccess) {
        this.assistantMessage = assistantMessage;
        this.conversationId   = conversationId;
        this.toolUsed         = toolUsed;
        this.toolSuccess      = toolSuccess;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /** Direct LLM reply — no tool was called. */
    public static ChatResponse direct(String message, String conversationId) {
        return new ChatResponse(message, conversationId, null, null);
    }

    /** Reply produced after a tool was called. */
    public static ChatResponse withTool(String message, String conversationId,
                                        String toolUsed, boolean toolSuccess) {
        return new ChatResponse(message, conversationId, toolUsed, toolSuccess);
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String  getAssistantMessage()            { return assistantMessage; }
    public void    setAssistantMessage(String v)    { this.assistantMessage = v; }

    public String  getConversationId()              { return conversationId; }
    public void    setConversationId(String v)      { this.conversationId = v; }

    public String  getToolUsed()                    { return toolUsed; }
    public void    setToolUsed(String v)            { this.toolUsed = v; }

    public Boolean getToolSuccess()                 { return toolSuccess; }
    public void    setToolSuccess(Boolean v)        { this.toolSuccess = v; }
}