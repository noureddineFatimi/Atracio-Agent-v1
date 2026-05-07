package atracio.agent.agent;

import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;


import com.google.genai.Client;

import org.springframework.ai.chat.messages.Message;

public class ConversationServiceTest {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceTest.class);
    
    private final Client client = Client.builder()
                                    .apiKey(System.getenv("MODEL_API_KEY"))
                                    .build();
    private final ChatModel GenAIChatModel = GoogleGenAiChatModel.builder()
                                 .genAiClient(client)
                                 .defaultOptions(
                                     GoogleGenAiChatOptions.builder()
                                         .model("gemini-3.1-flash-lite-preview")
                                         .temperature(2.0) 
                                         .build()
                                 )
                                 .build();
    
    private final ChatClient.Builder chatClientBuilder = ChatClient.builder(GenAIChatModel);

    private final ChatMemoryRepository repository = new InMemoryChatMemoryRepository();

    private final MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                                                    .chatMemoryRepository(repository)
                                                    .maxMessages(10)
                                                    .build();

    private final ConversationService conversationService = new ConversationService(memory, chatClientBuilder);

    @Test
    void shouldStoreUserAssistantAndToolSummary() {
        String conversationId = "conv-1";

        // User message
        UserMessage userMessage = new UserMessage("Prix du BTC ?");
        conversationService.addUserMessageToConversation(conversationId, userMessage);

        // Assistant message
        AssistantMessage assistantMessage = new AssistantMessage("Je vérifie...");
        conversationService.addAssistantMessageToConversation(conversationId, assistantMessage);

        // Tool response (brut)
        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse(
                        "tool-1",
                        "crypto_price",
                        "{ \"price\": 67213.45 }"
                );

        conversationService.addToolResultSummaryToConversation(conversationId, toolResponse);

        // Vérification
        List<Message> messages = conversationService.getConversationById(conversationId);

        log.info("conversation={}", messages);

        assertThat(messages).hasSize(3);

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2)).isInstanceOf(ToolResponseMessage.class);

        ToolResponseMessage toolMessage = (ToolResponseMessage) messages.get(2);

        String summary = toolMessage.getResponses().get(0).responseData();

        log.info("summary={}", summary);

    }

    @Test
    public void testLogging() {
        log.info("logs here");
    }

}