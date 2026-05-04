package atracio.agent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import atracio.agent.tools.ToolDefinitionRegistry;
import atracio.agent.tools.ToolExecutor;
import atracio.agent.tools.ToolResponse;

@Component
public class AgentOrchestrator {

    private final ChatClient chatClient;
    private final ToolExecutor toolExecutor;

    public AgentOrchestrator(ChatClient.Builder chatClientBuilder, SystemPromptFactory systemPromptFactory,ToolDefinitionRegistry toolDefinitionRegistry, ToolExecutor toolExecutor) {
        this.chatClient = chatClientBuilder
                            .defaultSystem(systemPromptFactory.build())
                            .defaultToolCallbacks(ToolDefinitionRegistry.getToolsCallBackList(toolDefinitionRegistry.getAll()))
                            .defaultOptions(GoogleGenAiChatOptions.builder()
                                            .internalToolExecutionEnabled(false)
                                            .build())
                            .build();
        this.toolExecutor = toolExecutor;
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

    public String chat(String userInput) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            UserMessage userMessage = UserMessage.builder().text(userInput).build();
            List<Message> history = new ArrayList<>();
            history.add(userMessage);
            ChatResponse chatResponse = generate(history);
            AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
            history.add(assistantMessage);
            while (chatResponse.hasToolCalls()) {
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                    try {
                        Map<String, Object> arguments = objectMapper.readValue(toolCall.arguments(), new TypeReference<Map<String, Object>>() {});
                        ToolResponse toolResponse = toolExecutor.dispatche(toolCall.name(), arguments);
                        String toolResponseJson = objectMapper.writeValueAsString(toolResponse);
                        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResponseJson);
                        responses.add(response);
                    } catch (Exception e) {
                        ToolResponse toolResponse = ToolResponse.agentError(e.getMessage());
                        String toolResponseJson = objectMapper.writeValueAsString(toolResponse);
                        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResponseJson);
                        responses.add(response);
                    }
                }
                history.add(ToolResponseMessage.builder().responses(responses).build());
                chatResponse = generate(history);
            }
            return chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            return "A unknown error occured, please try again";
        }
    }
}