package atracio.agent.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

class GenAiChatProviderTest {

    private List<Message> validateConversationAndAddSystemPrompt(
            String systemPrompt,
            List<Message> messages) {

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

                if (current instanceof AssistantMessage aM
                        && aM.hasToolCalls()) {
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

    @Test
    void shouldRemoveLeadingToolResponse() {

        List<Message> messages = new ArrayList<>();

        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse(
                        "tool-id",
                        "weather_tool",
                        "{\"temp\":23}");

        messages.add(ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build());

        messages.add(new UserMessage("Hello"));

        List<Message> result =
                validateConversationAndAddSystemPrompt(
                        "system prompt",
                        messages);

        assertEquals(2, result.size());

        assertInstanceOf(SystemMessage.class, result.get(0));
        assertInstanceOf(UserMessage.class, result.get(1));
    }

    @Test
    void shouldRemoveLeadingAssistantToolCall() {

        List<Message> messages = new ArrayList<>();

        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall(
                        "tool-id",
                        "function",
                        "weather_tool",
                        "{\"city\":\"Paris\"}");

        AssistantMessage assistantToolCall =
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall))
                        .build();

        messages.add(assistantToolCall);

        messages.add(new UserMessage("Hi"));

        List<Message> result =
                validateConversationAndAddSystemPrompt(
                        "system prompt",
                        messages);

        assertEquals(2, result.size());

        assertInstanceOf(SystemMessage.class, result.get(0));
        assertInstanceOf(UserMessage.class, result.get(1));
    }

    @Test
    void shouldKeepValidConversation() {

        List<Message> messages = new ArrayList<>();

        messages.add(new UserMessage("Hello"));
        messages.add(new AssistantMessage("Hi there"));

        List<Message> result =
                validateConversationAndAddSystemPrompt(
                        "system prompt",
                        messages);

        assertEquals(3, result.size());

        assertInstanceOf(SystemMessage.class, result.get(0));
        assertInstanceOf(UserMessage.class, result.get(1));
        assertInstanceOf(AssistantMessage.class, result.get(2));
    }

    @Test
    void shouldReturnOnlySystemPromptWhenMessagesEmpty() {

        List<Message> result =
                validateConversationAndAddSystemPrompt(
                        "system prompt",
                        new ArrayList<>());

        assertEquals(1, result.size());

        assertInstanceOf(SystemMessage.class, result.get(0));
    }
}
