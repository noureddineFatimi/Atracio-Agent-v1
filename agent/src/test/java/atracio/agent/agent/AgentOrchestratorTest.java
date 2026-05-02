package atracio.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;


import atracio.agent.atracio.AtracioBackendClient;
import atracio.agent.atracio.AtracioBackendClientMock;
import atracio.agent.atracio.AtracioErrorMapper;
import atracio.agent.atracio.AtracioUrlResolver;
import atracio.agent.tools.ToolDefinitionRegistry;
import atracio.agent.tools.ToolExecutor;
import atracio.agent.tools.ToolResponse;
import atracio.agent.tools.ToolShemas;

public class AgentOrchestratorTest {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorTest.class);
    Client client = Client.builder()
                    .apiKey("AIzaSyDCRskfGolPgmvEKFbAsA8-NSWhUhkW2oE")
                    .build();
     ChatModel GenAIChatModel = GoogleGenAiChatModel.builder()
                                 .genAiClient(client)
                                 .defaultOptions(
                                     GoogleGenAiChatOptions.builder()
                                         .model("gemini-2.5-flash")
                                         .temperature(2.0) // ton modèle NVIDIA
                                         .build()
                                 )
                                 .build();
    ObjectMapper objectMapper = new ObjectMapper();
    private ChatClient.Builder builder = ChatClient.builder(GenAIChatModel);
    private ToolShemas toolShemas = new ToolShemas();
    private ToolDefinitionRegistry toolDefinitionRegistry = new ToolDefinitionRegistry(toolShemas);
    private AtracioBackendClientMock atracioBackendClientMock = new AtracioBackendClientMock();
    private AtracioUrlResolver atracioUrlResolver = new AtracioUrlResolver("https://demo.prod.atracio.com");
    private AtracioErrorMapper atracioErrorMapper = new AtracioErrorMapper();
    private ToolExecutor toolExecutor = new ToolExecutor(atracioBackendClientMock, atracioErrorMapper, atracioUrlResolver);
    private AgentOrchestrator agentOrchestrator = new AgentOrchestrator(builder, toolDefinitionRegistry, toolExecutor);

    @Test 
    public void orchestrate() throws JsonProcessingException {
        ChatResponse chatResponse = agentOrchestrator.generate("What is the details of Sale Order document with id 334");
        assertThat(chatResponse).isNotNull();
        log.info("chatResponse={}", chatResponse);
        for (ToolCall toolCall : chatResponse.getResult().getOutput().getToolCalls()) {
            log.info("arguments={}", toolCall.arguments());
            log.info("\t-------------------------------------------------------------------------");
            Map<String, Object> arguments = objectMapper.readValue(toolCall.arguments(), new TypeReference<Map<String, Object>>() {});
            log.info("arguments_map={}", arguments);
        }
    }

    @Test
    public void chat() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            UserMessage userMessage = UserMessage.builder().text("What is the details of Sale Order document with id -30").build();
            List<Message> history = new ArrayList<>();
            history.add(userMessage);
            log.info("user message={}", userMessage.getText());
            ChatResponse chatResponse = agentOrchestrator.generate(history);
            AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
            history.add(assistantMessage);
            log.info("assistant message={}", assistantMessage.getText());
            while (chatResponse.hasToolCalls()) {
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                    log.info("tool={} will be executed with arguments={}", toolCall.name(), toolCall.arguments());
                    try {
                        Map<String, Object> arguments = objectMapper.readValue(toolCall.arguments(), new TypeReference<Map<String, Object>>() {});
                        ToolResponse toolResponse = toolExecutor.dispatche(toolCall.name(), arguments);
                        String toolResponseJson = objectMapper.writeValueAsString(toolResponse);
                        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResponseJson);
                        responses.add(response);
                        log.info("tool response={}", response.responseData());
                    } catch (Exception e) {
                        ToolResponse toolResponse = ToolResponse.agentError(e.getMessage());
                        String toolResponseJson = objectMapper.writeValueAsString(toolResponse);
                        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResponseJson);
                        responses.add(response);
                        log.info("tool response", response.responseData());
                    }
                }
                history.add(ToolResponseMessage.builder().responses(responses).build());
                chatResponse = agentOrchestrator.generate(history);
            }
            log.info("final response: {}",chatResponse.getResult().getOutput().getText()); 
        } catch (Exception e) {
            log.info("final response: {}","A unknown error occured, please try again, some details of the error: " + e.getMessage()); 
        }
    }

    @Test
    public void testLogging() {
        log.info("logs here");
    }
}