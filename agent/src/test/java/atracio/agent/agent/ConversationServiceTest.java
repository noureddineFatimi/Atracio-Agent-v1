package atracio.agent.agent;

import atracio.agent.atracio.AtracioErrorMapper;
import atracio.agent.tools.ToolResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationServiceTest {

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // Basic message flow
    // -------------------------------------------------------------------------

    @Nested
    class BasicFlow {

        @Test
        void emptyHistoryForNewConversation() {
            assertThat(service.getHistory("new-conv")).isEmpty();
        }

        @Test
        void addUserMessage() {
            service.addUserMessage("c1", "Show me sales orders");

            List<Map<String, Object>> history = service.getHistory("c1");
            assertThat(history).hasSize(1);
            assertThat(history.get(0).get("role")).isEqualTo("user");
            assertThat(history.get(0).get("content")).isEqualTo("Show me sales orders");
        }

        @Test
        void addAssistantMessage() {
            service.addAssistantMessage("c1", "Here are the orders.");

            Map<String, Object> msg = service.getHistory("c1").get(0);
            assertThat(msg.get("role")).isEqualTo("assistant");
            assertThat(msg.get("content")).isEqualTo("Here are the orders.");
        }

        @Test
        void addAssistantToolCall() {
            service.addAssistantToolCall("c1", "call_001", "document.search",
                    "{\"entity\":\"SalesOrder\"}");

            Map<String, Object> msg = service.getHistory("c1").get(0);
            assertThat(msg.get("role")).isEqualTo("assistant");
            assertThat(msg.get("content")).isNull();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls =
                    (List<Map<String, Object>>) msg.get("tool_calls");
            assertThat(toolCalls).hasSize(1);
            assertThat(toolCalls.get(0).get("id")).isEqualTo("call_001");

            @SuppressWarnings("unchecked")
            Map<String, Object> fn =
                    (Map<String, Object>) toolCalls.get(0).get("function");
            assertThat(fn.get("name")).isEqualTo("document.search");
        }

        @Test
        void fullConversationTurn() {
            service.addUserMessage("c1", "Find purchase orders");
            service.addAssistantToolCall("c1", "call_001",
                    "document.search", "{\"entity\":\"PurchaseOrder\"}");
            service.addToolResult("c1", "call_001", "document.search",
                    ToolResponse.success("document.search",
                            Map.of("content", List.of()), "demo", "/entities/list/PurchaseOrder"));
            service.addAssistantMessage("c1", "I found 0 purchase orders.");

            assertThat(service.size("c1")).isEqualTo(4);

            List<Map<String, Object>> history = service.getHistory("c1");
            assertThat(history.get(0).get("role")).isEqualTo("user");
            assertThat(history.get(1).get("role")).isEqualTo("assistant");
            assertThat(history.get(2).get("role")).isEqualTo("tool");
            assertThat(history.get(3).get("role")).isEqualTo("assistant");
        }
    }

    // -------------------------------------------------------------------------
    // Tool result summarisation
    // -------------------------------------------------------------------------

    @Nested
    class ToolResultSummarisation {

        @Test
        void successResultContainsOkTrueAndData() {
            service.addToolResult("c1", "call_001", "document.search",
                    ToolResponse.success("document.search",
                            Map.of("totalElements", 2), "demo", "/entities/list/SalesOrder"));

            Map<String, Object> msg = service.getHistory("c1").get(0);
            String content = (String) msg.get("content");
            assertThat(content).contains("ok: true");
            assertThat(content).contains("document.search");
            assertThat(content).contains("totalElements");
        }

        @Test
        void errorResultContainsOkFalseAndCode() {
            AtracioErrorMapper mapper = new AtracioErrorMapper();
            ToolResponse errorResponse = ToolResponse.error(
                    "document.search",
                    mapper.map(401, Map.of("code", "access_token_expired", "message", "Expired.")),
                    "demo", "/entities/list/SalesOrder");

            service.addToolResult("c1", "call_001", "document.search", errorResponse);

            Map<String, Object> msg = service.getHistory("c1").get(0);
            String content = (String) msg.get("content");
            assertThat(content).contains("ok: false");
            assertThat(content).contains("unauthorized");
        }

        @Test
        void largePayloadIsTruncated() {
            // Build a data payload whose JSON will exceed 2000 chars
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 300; i++) sb.append("item-").append(i).append(",");
            Map<String, Object> bigData = Map.of("items", sb.toString());

            service.addToolResult("c1", "call_001", "wms.get_article_stock_summary",
                    ToolResponse.success("wms.get_article_stock_summary",
                            bigData, "demo", "/warehouse/article/quantity/1"));

            String content = (String) service.getHistory("c1").get(0).get("content");
            assertThat(content).contains("[truncated]");
        }
    }

    // -------------------------------------------------------------------------
    // Trimming
    // -------------------------------------------------------------------------

    @Nested
    class Trimming {

        @Test
        void historyTrimsWhenExceedingMax() {
            for (int i = 0; i < ConversationService.MAX_MESSAGES + 5; i++) {
                service.addUserMessage("c1", "message " + i);
            }
            assertThat(service.size("c1")).isEqualTo(ConversationService.MAX_MESSAGES);
        }

        @Test
        void oldestMessagesAreRemovedFirst() {
            for (int i = 0; i < ConversationService.MAX_MESSAGES + 1; i++) {
                service.addUserMessage("c1", "message " + i);
            }
            // message 0 should be gone, message 1 is now the oldest
            String oldestContent = (String) service.getHistory("c1").get(0).get("content");
            assertThat(oldestContent).isEqualTo("message 1");
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Nested
    class Lifecycle {

        @Test
        void clearRemovesHistory() {
            service.addUserMessage("c1", "Hello");
            service.clear("c1");
            assertThat(service.getHistory("c1")).isEmpty();
            assertThat(service.size("c1")).isEqualTo(0);
        }

        @Test
        void differentConversationsAreIsolated() {
            service.addUserMessage("conv-A", "Message for A");
            service.addUserMessage("conv-B", "Message for B");

            assertThat(service.size("conv-A")).isEqualTo(1);
            assertThat(service.size("conv-B")).isEqualTo(1);

            String contentA = (String) service.getHistory("conv-A").get(0).get("content");
            String contentB = (String) service.getHistory("conv-B").get(0).get("content");

            assertThat(contentA).isEqualTo("Message for A");
            assertThat(contentB).isEqualTo("Message for B");
        }

        @Test
        void getHistoryReturnsImmutableSnapshot() {
            service.addUserMessage("c1", "Hello");
            List<Map<String, Object>> snapshot = service.getHistory("c1");

            // Adding another message should not affect the snapshot
            service.addUserMessage("c1", "World");
            assertThat(snapshot).hasSize(1);
        }
    }
}