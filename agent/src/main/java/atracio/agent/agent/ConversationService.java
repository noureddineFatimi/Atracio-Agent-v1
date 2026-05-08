package atracio.agent.agent;

import atracio.agent.tools.ToolResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory conversation histories per conversationId.
 *
 * Each conversation is a list of messages in the OpenAI chat format:
 *   { "role": "user" | "assistant" | "tool", "content": "..." }
 *
 * Design rules (from guide):
 *   - Max MAX_MESSAGES messages retained per conversation (oldest trimmed first)
 *   - Tool results are summarised before storage — never stored as full raw payloads
 *   - No persistence between JVM restarts (v1 scope)
 *   - Thread-safe via ConcurrentHashMap + synchronized history lists
 *
 * Roles used:
 *   user      : message from the human
 *   assistant : message or tool_call decision from the LLM
 *   tool      : result of a tool execution returned to the LLM
 */
@Component
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    /** Maximum number of messages kept per conversation before trimming. */
    static final int MAX_MESSAGES = 20;

    private final ConcurrentHashMap<String, List<Map<String, Object>>> histories =
            new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public ConversationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot of the current conversation history.
     * Returns an empty list if the conversation has not started yet.
     */
    public List<Map<String, Object>> getHistory(String conversationId) {
        List<Map<String, Object>> history = histories.get(conversationId);
        if (history == null) return List.of();
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Appends a user message to the conversation history.
     */
    public void addUserMessage(String conversationId, String content) {
        append(conversationId, Map.of("role", "user", "content", content));
    }

    /**
     * Appends a plain assistant text message to the conversation history.
     */
    public void addAssistantMessage(String conversationId, String content) {
        append(conversationId, Map.of("role", "assistant", "content", content));
    }

    /**
     * Appends an assistant tool_call decision to the conversation history.
     *
     * @param toolCallId  the id provided by the LLM for this call (used to match tool result)
     * @param toolName    the tool name (e.g. "document.search")
     * @param arguments   the raw argument string as provided by the LLM
     */
    public void addAssistantToolCall(String conversationId,
                                     String toolCallId,
                                     String toolName,
                                     String arguments) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role",    "assistant");
        message.put("content", null);
        message.put("tool_calls", List.of(Map.of(
                "id",   toolCallId,
                "type", "function",
                "function", Map.of(
                        "name",      toolName,
                        "arguments", arguments
                )
        )));
        append(conversationId, message);
    }

    /**
     * Appends a tool result to the conversation history.
     *
     * The full ToolResponse payload is summarised before storage to avoid
     * bloating the context window with raw Atracio data.
     *
     * @param toolCallId the id from the matching assistant tool_call message
     * @param toolName   the tool name (e.g. "document.search")
     * @param result     the ToolResponse returned by ToolExecutor
     */
    public void addToolResult(String conversationId,
                              String toolCallId,
                              String toolName,
                              ToolResponse result) {
        String content = summarise(toolName, result);

        Map<String, Object> message = Map.of(
                "role",         "tool",
                "tool_call_id", toolCallId,
                "name",         toolName,
                "content",      content
        );
        append(conversationId, message);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Clears the history for a conversation. Useful for "reset" commands.
     */
    public void clear(String conversationId) {
        histories.remove(conversationId);
        log.debug("ConversationService: cleared history for {}", conversationId);
    }

    /**
     * Returns the number of messages currently stored for a conversation.
     */
    public int size(String conversationId) {
        List<Map<String, Object>> history = histories.get(conversationId);
        if (history == null) return 0;
        synchronized (history) {
            return history.size();
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void append(String conversationId, Map<String, Object> message) {
        List<Map<String, Object>> history = histories.computeIfAbsent(
                conversationId, k -> new ArrayList<>());

        synchronized (history) {
            history.add(message);
            trim(history);
        }

        log.debug("ConversationService: [{}] role={} size={}",
                conversationId,
                message.get("role"),
                history.size());
    }

    /**
     * Removes the oldest messages when the history exceeds MAX_MESSAGES.
     * Always keeps the system prompt slot available for the orchestrator.
     */
    private void trim(List<Map<String, Object>> history) {
        while (history.size() > MAX_MESSAGES) {
            history.remove(0);
            log.debug("ConversationService: trimmed oldest message");
        }
    }

    /**
     * Produces a compact, LLM-readable summary of a ToolResponse.
     *
     * Success: serialises the data payload as JSON (trimmed to 2000 chars).
     * Error:   emits a structured error block the LLM can act on.
     *
     * Full raw Atracio payloads are never stored — only the normalised data.
     */
    private String summarise(String toolName, ToolResponse result) {
        if (!result.isOk()) {
            ToolResponse.ErrorPayload err = result.getError();
            return """
                {
                    "tool": "%s",
                    "ok": false,
                    "error_code": "%s",
                    "error_message": "%s"
                }
                    """.formatted(toolName, err.code(), err.message());
        }

        try {
            String json = objectMapper.writeValueAsString(result.getData());
            // Trim very large payloads to avoid bloating the context window
            if (json.length() > 2000) {
                json = json.substring(0, 2000) + "... [truncated]";
                return """
                {
                    "tool": "%s",
                    "ok": true,
                    "dataPreview": "%s",
                    "truncated": true
                }
                    """.formatted(toolName, json);
            }
            else {
                return """
                {
                    "tool": "%s",
                    "ok": true,
                    "data": %s
                }
                    """.formatted(toolName, json);
            }
            
        } catch (JsonProcessingException ex) {
            log.warn("ConversationService: failed to serialise tool result for {}", toolName);
            return "tool: %s\nok: true\ndata: [serialisation error]".formatted(toolName);
        }
    }
}