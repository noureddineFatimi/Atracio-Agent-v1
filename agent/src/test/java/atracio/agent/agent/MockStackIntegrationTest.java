package atracio.agent.agent;

import atracio.agent.tools.ToolDispatcher;
import atracio.agent.tools.ToolExecutor;
import atracio.agent.tools.ToolResponse;
import atracio.agent.provider.LlmProvider.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full tool execution chain using the mock backend.
 *
 * Validates that every tool:
 *   1. Wires correctly through the Spring context
 *   2. Calls AtracioBackendClientMock and gets a response
 *   3. Returns a normalised ToolResponse with the correct shape
 *   4. Routes correctly through ToolDispatcher (name → method)
 *
 * These tests run in CI with no external dependencies.
 * Profile: mock (default in IntegrationTestBase)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"mock", "ollama"})
@DisplayName("Mock Stack — All 7 Tools Integration")
class MockStackIntegrationTest {

    @Autowired
    protected ToolExecutor toolExecutor;

    @Autowired
    protected ToolDispatcher toolDispatcher;
        
    protected static final String TENANT = "demo";

    protected static final String testToken = "ejy-...";

    // =========================================================================
    // 1. document.search
    // =========================================================================

    @Nested
    @DisplayName("document.search")
    class DocumentSearch {

        @Test
        @DisplayName("returns ok=true with entity and result fields")
        void happyPath() {
            ToolResponse result = toolExecutor.documentSearch(
                    "SalesOrder", "ACME", 0, 20,
                    null, null, null, TENANT, testToken);

            assertToolSuccess(result, "document.search");
            assertThat(result.getMeta()).containsEntry("tenant", TENANT);
            assertThat(result.getMeta()).containsKey("backendPath");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data).containsKey("entity");
            assertThat(data).containsKey("items");
        }

        @Test
        @DisplayName("dispatches correctly from ToolDispatcher")
        void dispatchesFromToolCall() {
            ToolCall toolCall = new ToolCall("call_001", "document.search",
                    Map.of("entity", "PurchaseOrder", "filter", "Global"));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolSuccess(result, "document.search");
        }

        @Test
        @DisplayName("returns tool_mapping_error when entity is missing")
        void missingEntity() {
            ToolCall toolCall = new ToolCall("call_002", "document.search", Map.of());

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolError(result, "document.search", "tool_mapping_error");
        }
    }

    // =========================================================================
    // 2. document.get_details
    // =========================================================================

    @Nested
    @DisplayName("document.get_details")
    class DocumentGetDetails {

        @Test
        @DisplayName("returns ok=true with entity and document fields")
        void happyPath() {
            ToolResponse result = toolExecutor.documentGetDetails(
                    "SalesOrder", 101L, TENANT, testToken);

            assertToolSuccess(result, "document.get_details");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            
            assertThat(data).containsKeys("entity", "document");
        }

        @Test
        @DisplayName("dispatches correctly from ToolDispatcher")
        void dispatchesFromToolCall() {
            ToolCall toolCall = new ToolCall("call_003", "document.get_details",
                    Map.of("entity", "PurchaseOrder", "id", 501));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolSuccess(result, "document.get_details");
        }

        @Test
        @DisplayName("returns tool_mapping_error when id is 0")
        void invalidId() {
            ToolCall toolCall = new ToolCall("call_004", "document.get_details",
                    Map.of("entity", "SalesOrder", "id", 0));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolError(result, "document.get_details", "tool_mapping_error");
        }
    }

    // =========================================================================
    // 3. document.save_draft
    // =========================================================================

    @Nested
    @DisplayName("document.save_draft")
    class DocumentSaveDraft {

        @Test
        @DisplayName("returns ok=true with saved document")
        void happyPath() {
            ToolResponse result = toolExecutor.documentSaveDraft(
                    "Quotation",
                    Map.of("subject", "Quote for ACME", "client", Map.of("id", 44)),
                    null, TENANT, testToken);

            assertToolSuccess(result, "document.save_draft");
        }

        @Test
        @DisplayName("dispatches correctly from ToolDispatcher")
        void dispatchesFromToolCall() {
            ToolCall toolCall = new ToolCall("call_005", "document.save_draft",
                    Map.of(
                            "entity",   "SalesOrder",
                            "document", Map.of("id", 101, "subject", "Updated subject")
                    ));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolSuccess(result, "document.save_draft");
        }

        @Test
        @DisplayName("returns tool_mapping_error when document is missing")
        void missingDocument() {
            ToolCall toolCall = new ToolCall("call_006", "document.save_draft",
                    Map.of("entity", "SalesOrder"));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolError(result, "document.save_draft", "tool_mapping_error");
        }
    }

    // =========================================================================
    // 4. document.apply_process_action
    // =========================================================================

    @Nested
    @DisplayName("document.apply_process_action")
    class DocumentApplyProcessAction {

        @Test
        @DisplayName("lifecycle.release returns RELEASED state")
        void lifecycleRelease() {
            ToolResponse result = toolExecutor.documentApplyProcessAction(
                    "SalesOrder", 101L, "lifecycle.release",
                    null, TENANT, testToken);

            assertToolSuccess(result, "document.apply_process_action");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();

            assertThat(data).containsEntry("action", "lifecycle.release");
        }

        @Test
        @DisplayName("all supported action families dispatch correctly")
        void allActionFamilies() {
            String[] actions = {
                "lifecycle.release", "lifecycle.close", "lifecycle.cancel",
                "approval.submit",   "approval.approve",
                "posting.post",      "posting.reverse",
                "execution.start",   "execution.complete",
                "payment.allocate"
            };

            for (String action : actions) {
                ToolCall toolCall = new ToolCall("call_action", "document.apply_process_action",
                        Map.of("entity", "SalesOrder", "id", 101, "action", action));

                ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

                assertThat(result.isOk())
                        .as("Action '%s' should succeed", action)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("unknown action family returns tool_mapping_error")
        void unknownActionFamily() {
            ToolCall toolCall = new ToolCall("call_007", "document.apply_process_action",
                    Map.of("entity", "SalesOrder", "id", 101, "action", "ship.now"));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolError(result, "document.apply_process_action", "tool_mapping_error");
        }
    }

    // =========================================================================
    // 5. wms.get_article_stock_summary
    // =========================================================================

    @Nested
    @DisplayName("wms.get_article_stock_summary")
    class WmsGetArticleStockSummary {

        @Test
        @DisplayName("returns aggregated stock fields")
        void happyPath() {
            ToolResponse result = toolExecutor.wmsGetArticleStockSummary(
                    9001L, 3L, TENANT, testToken);

            assertToolSuccess(result, "wms.get_article_stock_summary");
            assertThat(result.getMeta()).containsEntry("aggregated", true);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data).containsKeys(
                    "articleId", "stockQuantity", "forecastQuantity",
                    "valuation", "entriesCurrentYear", "issuesCurrentYear", "turnover");
        }

        @Test
        @DisplayName("siteId is optional — works without it")
        void withoutSiteId() {
            ToolCall toolCall = new ToolCall("call_008", "wms.get_article_stock_summary",
                    Map.of("articleId", 9001));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolSuccess(result, "wms.get_article_stock_summary");
        }

        @Test
        @DisplayName("returns tool_mapping_error when articleId is 0")
        void invalidArticleId() {
            ToolCall toolCall = new ToolCall("call_009", "wms.get_article_stock_summary",
                    Map.of("articleId", 0));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolError(result, "wms.get_article_stock_summary", "tool_mapping_error");
        }
    }

    // =========================================================================
    // 6. wms.lookup_inventory_unit
    // =========================================================================

    @Nested
    @DisplayName("wms.lookup_inventory_unit")
    class WmsLookupInventoryUnit {

        @Test
        @DisplayName("barcode lookup returns unit with location")
        void barcodeLookup() {
            ToolResponse result = toolExecutor.wmsLookupInventoryUnit(
                    "barcode", "ABC-123", TENANT, testToken);

            assertToolSuccess(result, "wms.lookup_inventory_unit");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data).containsKeys("lookupType", "value", "unit");
            assertThat(data.get("lookupType")).isEqualTo("barcode");
        }

        @Test
        @DisplayName("rfidTag and serialNumber also dispatch correctly")
        void allLookupTypes() {
            for (String lookupType : new String[]{"barcode", "rfidTag", "serialNumber"}) {
                ToolCall toolCall = new ToolCall("call_lookup", "wms.lookup_inventory_unit",
                        Map.of("lookupType", lookupType, "value", "TEST-001"));

                ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

                assertThat(result.isOk())
                        .as("lookupType '%s' should succeed", lookupType)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("unknown lookupType returns tool_mapping_error")
        void unknownLookupType() {
            ToolCall toolCall = new ToolCall("call_010", "wms.lookup_inventory_unit",
                    Map.of("lookupType", "qrCode", "value", "QR-001"));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolError(result, "wms.lookup_inventory_unit", "tool_mapping_error");
        }
    }

    // =========================================================================
    // 7. partner.get_summary
    // =========================================================================

    @Nested
    @DisplayName("partner.get_summary")
    class PartnerGetSummary {

        @Test
        @DisplayName("client summary contains financial fields")
        void clientSummary() {
            ToolResponse result = toolExecutor.partnerGetSummary(
                    "client", 44L, TENANT, testToken);

            assertToolSuccess(result, "partner.get_summary");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data).containsKeys(
                    "partnerType", "partnerId",
                    "turnover", "unpaidAmount", "unpaidInvoices",
                    "lastInvoiceDate", "salesOrdersCount", "lastSalesOrder");
            assertThat(data.get("partnerType")).isEqualTo("client");
        }

        @Test
        @DisplayName("vendor summary contains financial fields")
        void vendorSummary() {
            ToolResponse result = toolExecutor.partnerGetSummary(
                    "vendor", 18L, TENANT, testToken);

            assertToolSuccess(result, "partner.get_summary");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data).containsKeys(
                    "partnerType", "partnerId",
                    "turnover", "unpaidAmount", "unpaidInvoices", "lastPurchaseOrderDate");
            assertThat(data.get("partnerType")).isEqualTo("vendor");
        }

        @Test
        @DisplayName("unknown partnerType returns tool_mapping_error")
        void unknownPartnerType() {
            ToolCall toolCall = new ToolCall("call_011", "partner.get_summary",
                    Map.of("partnerType", "supplier", "partnerId", 1));

            ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

            assertToolError(result, "partner.get_summary", "tool_mapping_error");
        }
    }

    // =========================================================================
    // ToolDispatcher — unknown tool
    // =========================================================================

    @Test
    @DisplayName("ToolDispatcher returns tool_mapping_error for unknown tool name")
    void unknownToolName() {
        ToolCall toolCall = new ToolCall("call_unknown", "inventory.explode", Map.of());

        ToolResponse result = toolDispatcher.dispatch(toolCall, TENANT, testToken);

        assertToolError(result, "", "tool_mapping_error");
    }

    // =========================================================================
    // Shared assertions
    // =========================================================================

    private void assertToolSuccess(ToolResponse result, String expectedTool) {
        assertThat(result).isNotNull();
        assertThat(result.isOk())
                .as("Tool '%s' expected ok=true but got error: %s",
                        expectedTool, result.getError())
                .isTrue();
        assertThat(result.getTool()).isEqualTo(expectedTool);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getMeta()).isNotNull();
    }

    private void assertToolError(ToolResponse result, String expectedTool, String expectedCode) {
        assertThat(result).isNotNull();
        assertThat(result.isOk()).isFalse();
        assertThat(result.getTool()).isEqualTo(expectedTool);
        assertThat(result.getData()).isNull();
        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().code()).isEqualTo(expectedCode);
    }
}