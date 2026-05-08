package atracio.agent.agent;

import atracio.agent.dto.ChatRequest;
import atracio.agent.dto.ChatResponse;
import atracio.agent.provider.LlmProvider;
import atracio.agent.provider.LlmProvider.LlmResponse;
import atracio.agent.provider.LlmProvider.ToolCall;
import atracio.agent.tools.ToolDefinitionRegistry;
import atracio.agent.tools.ToolDispatcher;
import atracio.agent.tools.ToolResponse;
import atracio.agent.tools.ToolShemas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock private LlmProvider         llmProvider;
    @Mock private ToolDispatcher      toolDispatcher;

    private SystemPromptFactory    systemPromptFactory;
    private ConversationService    conversationService;
    private ToolDefinitionRegistry toolDefinitionRegistry;
    private AgentOrchestrator      orchestrator;
    private ToolShemas toolShemas;

    private static final String CONV_ID = "conv-test-001";
    private static final String TENANT  = "demo";
    private static final String TOKEN   = "eyJ.test.token";

    @BeforeEach
    void setUp() {
        systemPromptFactory    = new SystemPromptFactory();
        conversationService    = new ConversationService(new ObjectMapper());
        toolShemas = new ToolShemas();
        toolDefinitionRegistry = new ToolDefinitionRegistry(toolShemas);
        
        orchestrator = new AgentOrchestrator(
                llmProvider,
                systemPromptFactory,
                conversationService,
                toolDefinitionRegistry,
                toolDispatcher
        );
    }

    private ChatRequest request(String message) {
        return new ChatRequest(message, CONV_ID, TENANT, TOKEN);
    }

    // -------------------------------------------------------------------------
    // Direct reply (no tool call)
    // -------------------------------------------------------------------------

    @Nested
    class DirectReply {

        @Test
        void llmRepliesDirectly() {
            when(llmProvider.chat(any(), any(), any()))
                    .thenReturn(LlmResponse.text("Hello! How can I help you?"));

            ChatResponse response = orchestrator.chat(request("Hello"));

            assertThat(response.getAssistantMessage()).isEqualTo("Hello! How can I help you?");
            assertThat(response.getConversationId()).isEqualTo(CONV_ID);
            assertThat(response.getToolUsed()).isNull();
            assertThat(response.getToolSuccess()).isNull();
        }

        @Test
        void historyContainsBothUserAndAssistantMessages() {
            when(llmProvider.chat(any(), any(), any()))
                    .thenReturn(LlmResponse.text("Sure, here you go."));

            orchestrator.chat(request("Show me orders"));

            List<Map<String, Object>> history = conversationService.getHistory(CONV_ID);
            assertThat(history).hasSize(2);
            assertThat(history.get(0).get("role")).isEqualTo("user");
            assertThat(history.get(1).get("role")).isEqualTo("assistant");
        }
    }

    // -------------------------------------------------------------------------
    // Tool call flow
    // -------------------------------------------------------------------------

    @Nested
    class ToolCallFlow {

        @Test
        void llmRequestsToolThenProducesFinalReply() {
            ToolCall toolCall = new ToolCall("call_001", "document.search",
                    Map.of("entity", "SalesOrder", "filter", "ACME"));

            // First call → tool request; second call → final reply
            when(llmProvider.chat(any(), any(), any()))
                    .thenReturn(LlmResponse.toolCall(toolCall))
                    .thenReturn(LlmResponse.text("I found 2 sales orders for ACME."));

            when(toolDispatcher.dispatch(eq(toolCall), eq(TENANT), eq(TOKEN)))
                    .thenReturn(ToolResponse.success("document.search",
                            Map.of("totalElements", 2), TENANT, "/entities/list/SalesOrder"));

            ChatResponse response = orchestrator.chat(request("Show me orders for ACME"));

            assertThat(response.getAssistantMessage())
                    .isEqualTo("I found 2 sales orders for ACME.");
            assertThat(response.getToolUsed()).isEqualTo("document.search");
            assertThat(response.getToolSuccess()).isTrue();
        }

        @Test
        void historyHas4MessagesAfterToolCallTurn() {
            ToolCall toolCall = new ToolCall("call_002", "document.search",
                    Map.of("entity", "PurchaseOrder"));

            when(llmProvider.chat(any(), any(), any()))
                    .thenReturn(LlmResponse.toolCall(toolCall))
                    .thenReturn(LlmResponse.text("Here are your purchase orders."));

            when(toolDispatcher.dispatch(any(), any(), any()))
                    .thenReturn(ToolResponse.success("document.search",
                            Map.of("totalElements", 1), TENANT, "/entities/list/PurchaseOrder"));

            orchestrator.chat(request("Find purchase orders"));

            List<Map<String, Object>> history = conversationService.getHistory(CONV_ID);
            // user | assistant(tool_call) | tool(result) | assistant(final)
            assertThat(history).hasSize(4);
            assertThat(history.get(0).get("role")).isEqualTo("user");
            assertThat(history.get(1).get("role")).isEqualTo("assistant");
            assertThat(history.get(2).get("role")).isEqualTo("tool");
            assertThat(history.get(3).get("role")).isEqualTo("assistant");
        }

        @Test
        void toolFailureIsReflectedInResponse() {
            ToolCall toolCall = new ToolCall("call_003", "document.get_details",
                    Map.of("entity", "SalesOrder", "id", 999));

            when(llmProvider.chat(any(), any(), any()))
                    .thenReturn(LlmResponse.toolCall(toolCall))
                    .thenReturn(LlmResponse.text("The document was not found."));

            when(toolDispatcher.dispatch(any(), any(), any()))
                    .thenReturn(ToolResponse.toolError("document.get_details",
                            "not_found", "SalesOrder 999 not found.", TENANT, "/entities/details/" + "SalesOrder" + "/" + "999"));

            ChatResponse response = orchestrator.chat(request("Get order 999"));

            assertThat(response.getToolSuccess()).isFalse();
            assertThat(response.getToolUsed()).isEqualTo("document.get_details");
        }

        @Test
        void toolDispatcherIsCalledWithCorrectArguments() {
            ToolCall toolCall = new ToolCall("call_004", "partner.get_summary",
                    Map.of("partnerType", "client", "partnerId", 44));

            when(llmProvider.chat(any(), any(), any()))
                    .thenReturn(LlmResponse.toolCall(toolCall))
                    .thenReturn(LlmResponse.text("Client summary ready."));

            when(toolDispatcher.dispatch(any(), any(), any()))
                    .thenReturn(ToolResponse.success("partner.get_summary",
                            Map.of("turnover", 125000.0), TENANT, "/client/44"));

            orchestrator.chat(request("Show client 44 summary"));

            verify(toolDispatcher).dispatch(eq(toolCall), eq(TENANT), eq(TOKEN));
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Nested
    class Validation {

        @Test
        void nullRequestThrows() {
            assertThatThrownBy(() -> orchestrator.chat(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void blankUserMessageThrows() {
            assertThatThrownBy(() -> orchestrator.chat(new ChatRequest("  ", CONV_ID, TENANT, TOKEN)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("userMessage");
        }

        @Test
        void blankConversationIdThrows() {
            assertThatThrownBy(() -> orchestrator.chat(new ChatRequest("Hello", "", TENANT, TOKEN)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("conversationId");
        }

        @Test
        void blankBearerTokenThrows() {
            assertThatThrownBy(() -> orchestrator.chat(new ChatRequest("Hello", CONV_ID, TENANT, "")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bearerToken");
        }
    }

    // -------------------------------------------------------------------------
    // Multi-turn history
    // -------------------------------------------------------------------------

    @Test
    void secondTurnReceivesPreviousHistory() {
        when(llmProvider.chat(any(), any(), any()))
                .thenReturn(LlmResponse.text("I found your orders."))
                .thenReturn(LlmResponse.text("Here is more detail."));

        orchestrator.chat(request("Show orders"));
        orchestrator.chat(request("Give me more detail on order 101"));

        // 2 turns × 2 messages each = 4
        assertThat(conversationService.size(CONV_ID)).isEqualTo(4);
    }
}