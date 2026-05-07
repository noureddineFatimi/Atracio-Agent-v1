package atracio.agent.agent;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConversationService {

    private final ChatMemory chatMemory;
    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public ConversationService(ChatMemory chatMemory, ChatClient.Builder chatClientBuilder) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder.build();
        this.promptTemplate = PromptTemplate.builder()
                                .template("Summarize this tool result for conversation memory:\r\n" + "{tool_output}")
                                .build();
    }

    public void addUserMessageToConversation(String conversationId, UserMessage userMessage) {
        chatMemory.add(conversationId, userMessage);
    }

    public void addAssistantMessageToConversation(String conversationId, AssistantMessage assistantMessage) {
        chatMemory.add(conversationId, assistantMessage);
    }

    public void addToolResultSummaryToConversation(String conversationId, ToolResponseMessage.ToolResponse toolResponse) {
        String toolOutput = toolResponse.responseData();
        String toolName = toolResponse.name();
        String toolId = toolResponse.id();
        String toolResultSummary = chatClient.prompt(promptTemplate.render(Map.of("tool_output", toolOutput))).call().content();
        ToolResponseMessage.ToolResponse toolResponseSummary = new ToolResponseMessage.ToolResponse(toolId, toolName, toolResultSummary);
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                                                    .responses(List.of(toolResponseSummary))
                                                    .build();
        chatMemory.add(conversationId, toolResponseMessage);
    }

    public void addToolResultToConversation(String conversationId, ToolResponseMessage.ToolResponse toolResponse) {
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                                                    .responses(List.of(new ToolResponseMessage.ToolResponse(toolResponse.id(), toolResponse.name(),toolResponse.responseData())))
                                                    .build();
        chatMemory.add(conversationId, toolResponseMessage);
    }

    public ChatMemory getChatMemory(){
        return chatMemory;
    }

    public List<Message> getConversationById(String conversationId){
        return chatMemory.get(conversationId);
    }

}