package atracio.agent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.checkerframework.checker.units.qual.h;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.DefaultToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import atracio.agent.tools.ToolDefinitionRegistry;
import atracio.agent.tools.ToolExecutor;
import atracio.agent.tools.ToolResponse;

@Component
public class AgentOrchestrator {

    private final ChatClient chatClient;
    private final ToolExecutor toolExecutor;
    private final ConversationService conversationService;

    public AgentOrchestrator(ChatClient.Builder chatClientBuilder, SystemPromptFactory systemPromptFactory,ToolDefinitionRegistry toolDefinitionRegistry, ToolExecutor toolExecutor, ConversationService conversationService) {
        this.chatClient = chatClientBuilder
                            .defaultAdvisors(new SimpleLoggerAdvisor())
                            .defaultSystem(systemPromptFactory.build())
                            .defaultToolCallbacks(ToolDefinitionRegistry.getToolsCallBackList(toolDefinitionRegistry.getAll()))
                            .defaultOptions(GoogleGenAiChatOptions.builder()
                                            .internalToolExecutionEnabled(false)
                                            .build())
                            .build();
        this.toolExecutor = toolExecutor;
        this.conversationService = conversationService;
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

    public void addToolResponseMessage(ToolCall toolCall, String toolResponseJson, String conversationId) {
        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(getToolCallId(toolCall), toolCall.name(), toolResponseJson);
        conversationService.addToolResultToConversation(conversationId, response);
    }

    public String chat(String userInput) {
        String conversationId = "conv-001";
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            addUserMessage(conversationId, userInput);
            ChatResponse chatResponse = generate(conversationService.getConversationById(conversationId));
            while (chatResponse.hasToolCalls()) {
                AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
                conversationService.addAssistantMessageToConversation(conversationId, assistantMessage);
                for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                    try {
                        String toolResponseJson = executeTool(toolCall, objectMapper);
                        addToolResponseMessage(toolCall, toolResponseJson, conversationId);
                    } catch (Exception e) {
                        ToolResponse toolResponse = ToolResponse.agentError(e.getMessage());
                        String toolResponseJson = objectMapper.writeValueAsString(toolResponse);
                        addToolResponseMessage(toolCall, toolResponseJson, conversationId);
                    }
                }
                chatResponse = generate(conversationService.getConversationById(conversationId));
            }
            AssistantMessage finalMessage = (AssistantMessage) chatResponse.getResult().getOutput();
            conversationService.addAssistantMessageToConversation(conversationId, finalMessage);
            return finalMessage.getText();
        } catch (Exception e) {
            return "An unknown error occurred, please try again";
        }
    }
}