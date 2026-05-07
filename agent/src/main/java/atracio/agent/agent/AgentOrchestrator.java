package atracio.agent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;

import atracio.agent.tools.ToolDefinitionRegistry;
import atracio.agent.tools.ToolExecutor;
import atracio.agent.tools.ToolResponse;

@Component
public class AgentOrchestrator {

    private final ChatClient chatClient;
    private final ToolExecutor toolExecutor;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final ChatClient summarizerClient;
    private final PromptTemplate promptTemplate;

    public AgentOrchestrator(ChatClient.Builder chatClientBuilder, SystemPromptFactory systemPromptFactory,ToolDefinitionRegistry toolDefinitionRegistry, ToolExecutor toolExecutor, ConversationService conversationService, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder
                            .defaultAdvisors(new SimpleLoggerAdvisor())
                            .defaultSystem(systemPromptFactory.build())
                            .defaultToolCallbacks(ToolDefinitionRegistry.getToolsCallBackList(toolDefinitionRegistry.getAll()))
                            .defaultOptions(GoogleGenAiChatOptions.builder()
                                            .internalToolExecutionEnabled(false)
                                            .build())
                            .build();
        this.summarizerClient = chatClientBuilder.build();
        this.toolExecutor = toolExecutor;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.promptTemplate = PromptTemplate.builder()
                                .template("Summarize this tool result for conversation memory:\r\n" + "{tool_output}")
                                .build();
    }

    public ChatResponse generate(String userInput) {
        return chatClient.prompt(userInput)
            .call()
            .chatResponse();
    }

    public ChatResponse generate(List<Message> messagesList) {
        Prompt prompt = new Prompt(messagesList);
        return chatClient.prompt(prompt)
            .call()
            .chatResponse();
    }

    public String getToolCallId(ToolCall toolCall) {
        String toolCallId = (toolCall.id() != null && !toolCall.id().isBlank())
                    ? toolCall.id()
                    : UUID.randomUUID().toString();
        return toolCallId;
    }

    public String executeTool(ToolCall toolCall, ObjectMapper objectMapper) throws JsonProcessingException{
        Map<String, Object> arguments = objectMapper.readValue(toolCall.arguments(),
                            new TypeReference<Map<String, Object>>() {});
                    ToolResponse toolResponse = toolExecutor.dispatche(toolCall.name(), arguments);
                    String toolResponseJson = objectMapper.writeValueAsString(toolResponse);
        return toolResponseJson;
    }

    private void addUserMessage(String conversationId, String userInput) {
        UserMessage userMessage = UserMessage.builder().text(userInput).build();
        conversationService.addUserMessageToConversation(conversationId, userMessage);
    }

    public String addToolResponseMessage(ToolCall toolCall, String toolResponseJson, String conversationId) {
        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(getToolCallId(toolCall), toolCall.name(), toolResponseJson);
        conversationService.addToolResultToConversation(conversationId, response);
        return response.id();
    }

    public void addToolResponseMessageToTempConv(List<Message> temp_conv, String toolCallId, ToolCall toolCall, String toolResponseJson) {
        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(toolCallId, toolCall.name(), toolResponseJson);
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                                                                    .responses(List.of(toolResponse))
                                                                    .build();
        temp_conv.add(toolResponseMessage);
    }

    public AssistantMessage addAssistantMessage(ChatResponse chatResponse, String conversationId) {
        AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
        conversationService.addAssistantMessageToConversation(conversationId, assistantMessage);
        return assistantMessage;
    }

    public String summarizeToolResponse(String toolResponseJson) throws JsonProcessingException{
        String toolResponseJsonSummarized = summarizerClient.prompt(promptTemplate.render(Map.of("tool_output", toolResponseJson))).call().content();
        String toolJsonResultSummary = new ObjectMapper().writeValueAsString(Map.of("summary", toolResponseJsonSummarized));
        return toolJsonResultSummary;
   }

    public String chat(String userInput) {
        String conversationId = "conv-001";
        try {
            addUserMessage(conversationId, userInput);
            ChatResponse chatResponse = generate(conversationService.getConversationById(conversationId));
            while (chatResponse.hasToolCalls()) {
                AssistantMessage assistantMessage = addAssistantMessage(chatResponse, conversationId);
                List<Message> temp_conv = new ArrayList<>(conversationService.getConversationById(conversationId));
                for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                    try {
                        String toolResponseJson = executeTool(toolCall, objectMapper);
                        String toolCallId = addToolResponseMessage(toolCall, summarizeToolResponse(toolResponseJson), conversationId);
                        addToolResponseMessageToTempConv(temp_conv, toolCallId, toolCall, toolResponseJson);
                    } catch (Exception e) {
                        ToolResponse agentErrorToolResponse = ToolResponse.agentError(e.getMessage());
                        String toolResponseJson = objectMapper.writeValueAsString(agentErrorToolResponse);
                        String toolCallId = addToolResponseMessage(toolCall, toolResponseJson, conversationId);
                        addToolResponseMessageToTempConv(temp_conv, toolCallId, toolCall, toolResponseJson);
                    }
                }
                chatResponse = generate(temp_conv);
            }
            AssistantMessage finalMessage = addAssistantMessage(chatResponse, conversationId);
            return finalMessage.getText();
        } catch (Exception e) {
            String errorMessage = "An unknown error occurred, please try again";
            AssistantMessage errorAssistantMessage = AssistantMessage.builder().content(errorMessage).build();
            conversationService.addAssistantMessageToConversation(conversationId, errorAssistantMessage);
            return errorMessage;
        }
    }
}