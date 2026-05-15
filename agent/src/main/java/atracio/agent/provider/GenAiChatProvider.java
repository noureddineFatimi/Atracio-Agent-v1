package atracio.agent.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID; 

/**
 * LlmProvider implementation backed by Gemini via Spring AI.
 *
 * Active under profile "gemini".
 *
 * The logic is identical to OpenAiChatProvider — only the options class
 * and the Spring AI starter differ. Everything else (message conversion,
 * tool call parsing, response mapping) stays the same because Spring AI
 * normalises the provider differences behind ChatClient.
 */
@Component
@Profile("gemini")
public class GenAiChatProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GenAiChatProvider.class);

    private final ChatClient   chatClient;
    private final ObjectMapper objectMapper;

    public GenAiChatProvider(ChatClient genAichatClient,
                               ObjectMapper objectMapper) {
        this.chatClient   = genAichatClient;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // LlmProvider
    // -------------------------------------------------------------------------

    @Override
    public LlmResponse chat(String systemPrompt,
                            List<Map<String, Object>> history,
                            List<Map<String, Object>> tools) {

        List<Message> messages = buildMessages(systemPrompt, history);

        try {
            Prompt prompt = new Prompt(messages, buildOptions(tools));
            ChatResponse response = chatClient
                    .prompt(prompt)
                    .call()
                    .chatResponse();

            return parseResponse(response);

        } catch (Exception ex) {
            log.error("GenAIChatProvider: LLM call failed — {}", ex.getMessage(), ex);
            return LlmResponse.text(
                    "I'm sorry, I encountered an error communicating with Gemini. " +
                    "Please try again.");
        }
    }

    // -------------------------------------------------------------------------
    // Message conversion — history Map → Spring AI Message objects
    // -------------------------------------------------------------------------

    private List<Message> buildMessages(String systemPrompt,
                                        List<Map<String, Object>> history) {
        List<Message> messages = new ArrayList<>();

        for (Map<String, Object> entry : history) {
            String role    = (String) entry.get("role");
            Object content = entry.get("content");

            switch (role) {
                case "user" ->{
                    messages.add(new UserMessage((String) content));
                    log.info("GenAIChatProvider: user_message_in_prompt={}", new UserMessage((String) content));
                }

                case "assistant" -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> toolCalls =
                            (List<Map<String, Object>>) entry.get("tool_calls");

                    if (toolCalls != null && !toolCalls.isEmpty()) {

                        List<org.springframework.ai.chat.messages.AssistantMessage.ToolCall> toolCallsList = new ArrayList<>();

                        for (Map<String, Object> tc : toolCalls) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                            org.springframework.ai.chat.messages.AssistantMessage.ToolCall toolCall = new org.springframework.ai.chat.messages.AssistantMessage.ToolCall(tc.get("id") instanceof String id ? id : "", "function", fn.get("name")instanceof String name ? name : "", fn.get("arguments")instanceof String arguments ? arguments  : "");
                            toolCallsList.add(toolCall);
                        }
                        
                        AssistantMessage assistantMessage = AssistantMessage.builder()
                                                            .content("")
                                                            .toolCalls(toolCallsList)
                                                            .build();
                        messages.add(assistantMessage);
                        log.info("GenAIChatProvider: assistant_message_in_prompt={}", assistantMessage);
                    } else {
                        messages.add(new AssistantMessage(
                                content != null ? (String) content : ""));
                        log.info("GenAIChatProvider: assistant_message_in_prompt={}", new AssistantMessage(
                                content != null ? (String) content : ""));
                    }
                }

                case "tool" ->{
                    ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                                    (String) entry.get("tool_call_id"),
                                    (String) entry.get("name"),
                                    (String) entry.get("content"));
                    messages.add(ToolResponseMessage.builder()
                                    .responses(List.of(toolResponse))
                                    .build());
                    log.info("GenAIChatProvider: tool_response_message_in_prompt={}", ToolResponseMessage.builder()
                                    .responses(List.of(toolResponse))
                                    .build());
                }
                default ->
                    log.warn("GenAIChatProvider: unknown role '{}' — skipping", role);
            }
        }
        List<Message> validateMessages = validateConversationAndAddSystemPrompt(systemPrompt, messages);
        return validateMessages;
    }

    private List<Message> validateConversationAndAddSystemPrompt(String systemPrompt, List<Message> messages) {
        
        List<Message> validated = new ArrayList<>();

        if (messages == null || messages.isEmpty()) {
            validated.add(new SystemMessage(systemPrompt));
            return validated;
        }

        for (Message current : messages) {
            if (validated.isEmpty()) {
                if (current instanceof ToolResponseMessage) {
                    continue;
                }
                if (current instanceof AssistantMessage aM && aM.hasToolCalls()) {
                    continue;
                }
                validated.add(current);
                continue;
            }
            validated.add(current);
        }
        validated.add(0, new SystemMessage(systemPrompt));
        return validated;
    }

    // -------------------------------------------------------------------------
    // Options — gemini-specific chat options with tool definitions
    // -------------------------------------------------------------------------

    private static List<ToolCallback> getToolsCallBackList(List<Map<String, Object>> toolsShemasMap) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<ToolCallback> toolsCallBack = new ArrayList<>();
        try {
            for (Map<String,Object> toolDefinitionShema : toolsShemasMap) {
                Object functionObject = toolDefinitionShema.get("function");

                String name = functionObject instanceof Map<?, ?> fO ? fO.get("name") instanceof String n ? n : "" : "";
                String description = functionObject instanceof Map<?, ?> fO ? fO.get("description") instanceof String d ? d : "" : "";
                Map<?, ?> inputSchemaObject = functionObject instanceof Map<?, ?> fO ? fO.get("parameters") instanceof Map<?, ?> inputSchemaMap ? inputSchemaMap : Map.of() : Map.of();
                String inputSchema = objectMapper.writeValueAsString(inputSchemaObject);

                ToolCallback toolCallBack = toToolCallBack(name, description, inputSchema);

                toolsCallBack.add(toolCallBack);
            }
            return toolsCallBack;
        } catch (Exception e) {
            System.out.println(e);
            return toolsCallBack;
        }
    }

    private final static ToolCallback toToolCallBack(String name, String description, String inputSchema) {
        return FunctionToolCallback
                .builder(name, (String input) -> "not_executed") // no-op, exécution manuelle
                .description(description)
                .inputSchema(inputSchema)
                .inputType(String.class)
                .build();
    }

    private GoogleGenAiChatOptions buildOptions(List<Map<String, Object>> tools) {
        GoogleGenAiChatOptions.Builder builder = GoogleGenAiChatOptions.builder()
                .temperature(0.2);
        
        if (tools != null && !tools.isEmpty()) {
            builder = builder.internalToolExecutionEnabled(false)
            .toolCallbacks(getToolsCallBackList(tools));
        }
        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Response parsing — Spring AI ChatResponse → LlmResponse
    // -------------------------------------------------------------------------

    private LlmResponse parseResponse(ChatResponse response) {
        log.info("GenAIChatProvider: received response from LLM: {}", response);
        if (response == null || response.getResult() == null) {
            log.warn("GenAIChatProvider: empty response from LLM");
            return LlmResponse.text("I did not receive a response. Please try again.");
        }

        AssistantMessage message = response.getResult().getOutput();
        List<org.springframework.ai.chat.messages.AssistantMessage.ToolCall> toolCallsFromLlmResponse = message.getToolCalls();

        if (toolCallsFromLlmResponse != null && !toolCallsFromLlmResponse.isEmpty()) {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (org.springframework.ai.chat.messages.AssistantMessage.ToolCall toolCallFromLlmResponse : toolCallsFromLlmResponse) {
                String id = toolCallFromLlmResponse.id();
                if (id == null || id.isBlank()) {
                    id = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                }
                String              name = toolCallFromLlmResponse.name();
                String              args = toolCallFromLlmResponse.arguments();
                Map<String, Object> parsedArgs = parseArguments(args);

                log.debug("GenAIChatProvider: tool call id={} name={}", id, name);
                
                toolCalls.add(new ToolCall(id, name, parsedArgs));
            }
            return LlmResponse.toolCalls(toolCalls);
        }

        String text = message.getText();
        log.debug("GenAIChatProvider: text reply length={}", text != null ? text.length() : 0);
        return LlmResponse.text(text != null ? text : "");
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            log.warn("GenAIChatProvider: failed to parse tool arguments — {}", json);
            return Map.of();
        }
    }
} 