package atracio.agent.provider;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over LLM providers (OpenAI, Ollama).
 *
 * The orchestrator never calls OpenAI or Ollama directly — it always goes
 * through this interface. This lets Phase 5 swap providers via Spring profiles
 * without touching any orchestrator logic.
 *
 * Phase 4: OpenAiChatProvider implements this.
 * Phase 5: OllamaChatProvider implements this.
 */
public interface LlmProvider {

    /**
     * Sends a chat request to the underlying LLM and returns the response.
     *
     * @param systemPrompt the full system prompt from SystemPromptFactory
     * @param history      the current conversation history (role/content messages)
     * @param tools        tool definitions from ToolDefinitionRegistry (may be empty)
     * @return LlmResponse containing either a text reply or a tool call request
     */
    LlmResponse chat(String systemPrompt,
                     List<Map<String, Object>> history,
                     List<Map<String, Object>> tools);

    // -------------------------------------------------------------------------
    // LlmResponse — what the LLM returns after one completion
    // -------------------------------------------------------------------------

    /**
     * Represents one completion response from the LLM.
     *
     * The LLM either:
     *   A) Returns a text message  → hasToolCall() == false, getText() is set
     *   B) Requests a tool call    → hasToolCall() == true,  getToolCall() is set
     *
     * Never both at the same time.
     */
    class LlmResponse {

        private final String      text;
        private final List<ToolCall>    toolCalls;

        public LlmResponse(String text, List<ToolCall> toolCalls) {
            this.text     = text;
            this.toolCalls = toolCalls;
        }

        // Factory methods

        public static LlmResponse text(String text) {
            return new LlmResponse(text, null);
        }

        public static LlmResponse toolCalls(List<ToolCall> toolCalls) {
            return new LlmResponse(null, toolCalls);
        }

        // Accessors

        public boolean  hasToolCalls()  { return toolCalls != null; }
        public String   getText()      { return text; }
        public List<ToolCall> getToolCalls()  { return toolCalls; }
    }

    // -------------------------------------------------------------------------
    // ToolCall — the tool request from the LLM
    // -------------------------------------------------------------------------

    /**
     * Represents a single tool call requested by the LLM.
     *
     * Maps directly to the OpenAI tool_calls[0] structure:
     * {
     *   "id":       "call_abc123",
     *   "type":     "function",
     *   "function": {
     *     "name":      "document.search",
     *     "arguments": "{\"entity\": \"SalesOrder\"}"
     *   }
     * }
     */
    class ToolCall {

        private final String              id;
        private final String              name;
        private final Map<String, Object> arguments;

        public ToolCall(String id, String name, Map<String, Object> arguments) {
            this.id        = id;
            this.name      = name;
            this.arguments = arguments;
        }

        public String              getId()        { return id; }
        public String              getName()      { return name; }
        public Map<String, Object> getArguments() { return arguments; }
    }
}