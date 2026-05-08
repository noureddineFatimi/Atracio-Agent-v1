package atracio.agent.dto;

/**
 * Inbound request from the user — received by ChatController and passed to AgentOrchestrator.
 */
public class ChatRequest {

    /** The user's natural language message. */
    private String userMessage;

    /**
     * Stable identifier for this conversation.
     * The caller must generate and reuse it across turns to maintain history.
     * Example: a UUID generated on first message and stored client-side.
     */
    private String conversationId;

    /** Atracio tenant identifier (e.g. "demo"). */
    private String tenant;

    /**
     * The current user's Atracio bearer token.
     * Forwarded as-is to every backend call — never stored by the agent.
     */
    private String bearerToken;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public ChatRequest() {}

    public ChatRequest(String userMessage, String conversationId,
                       String tenant, String bearerToken) {
        this.userMessage     = userMessage;
        this.conversationId  = conversationId;
        this.tenant          = tenant;
        this.bearerToken     = bearerToken;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getUserMessage()             { return userMessage; }
    public void   setUserMessage(String v)     { this.userMessage = v; }

    public String getConversationId()          { return conversationId; }
    public void   setConversationId(String v)  { this.conversationId = v; }

    public String getTenant()                  { return tenant; }
    public void   setTenant(String v)          { this.tenant = v; }

    public String getBearerToken()             { return bearerToken; }
    public void   setBearerToken(String v)     { this.bearerToken = v; }
}