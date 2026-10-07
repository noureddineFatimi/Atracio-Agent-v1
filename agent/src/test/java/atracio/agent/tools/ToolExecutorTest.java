package atracio.agent.tools;

import atracio.agent.atracio.AtracioBackendClient;
import atracio.agent.atracio.AtracioBackendException;
import atracio.agent.atracio.AtracioErrorMapper;
import atracio.agent.atracio.AtracioUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock
    private AtracioBackendClient client;

    private AtracioErrorMapper errorMapper;
    private AtracioUrlResolver urlResolver;
    private ToolExecutor       executor;

    private static final String TENANT = "demo";
    private static final String TOKEN  = "eyJ.test.token";

    // Atracio success envelope
    private static Map<String, Object> successEnvelope(Object response) {
        return Map.of("status", "success", "response", response);
    }

    // Atracio business error envelope
    private static Map<String, Object> errorEnvelope(List<Map<String, Object>> errorListMessages) {
        return Map.of("status", "error", "response", errorListMessages);
    }

    @BeforeEach
    void setUp() {
        errorMapper = new AtracioErrorMapper();
        urlResolver = new AtracioUrlResolver("https://url");
        executor    = new ToolExecutor(client, errorMapper, urlResolver);
    }

    // =========================================================================
    // 1. document.search
    // =========================================================================

    @Nested
    class DocumentSearch {

        @Test
        void happyPath() {
            Map<String, Object> items = Map.of("content", List.of(
                    Map.of("id", 101, "documentNumber", "SO-000101")));

            when(client.listEntities(eq("SalesOrder"), any(), eq(TOKEN)))
                    .thenReturn(items);

            ToolResponse result = executor.documentSearch(
                    "SalesOrder", "ACME", 0, 20, null, null, null, TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            assertThat(result.getTool()).isEqualTo("document.search");
            assertThat(result.getMeta()).containsEntry("tenant", TENANT);
        }

        @Test
        void missingEntityReturnsToolError() {
            ToolResponse result = executor.documentSearch(
                    null, null, 0, 20, null, null, null, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
        }

        @Test
        void businessErrorIsMapped() {
            when(client.listEntities(eq("SalesOrder"), any(), eq(TOKEN)))
                    .thenReturn(errorEnvelope(List.of(Map.of("completeErrorMessage", "Filter value too long."), Map.of("completeErrorMessage", "asPage Field should be a bool."))));

            ToolResponse result = executor.documentSearch(
                    "SalesOrder", "x", 0, 20, null, null, null, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("validation_error");
            assertThat(result.getError().message()).isEqualTo("Filter value too long., asPage Field should be a bool.");
        }

        @Test
        void backendExceptionIsMapped() {
            when(client.listEntities(eq("SalesOrder"), any(), eq(TOKEN)))
                    .thenThrow(new AtracioBackendException(401,
                            Map.of("code", "access_token_expired", "message", "Expired.")));

            ToolResponse result = executor.documentSearch(
                    "SalesOrder", null, 0, 20, null, null, null, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("unauthorized");
            assertThat(result.getError().backendStatus()).isEqualTo(401);
        }
    }

    // =========================================================================
    // 2. document.get_details
    // =========================================================================

    @Nested
    class DocumentGetDetails {

        @Test
        void happyPath() {
            Map<String, Object> doc = Map.of("id", 501, "documentNumber", "PO-000501");

            when(client.getEntityDetails("PurchaseOrder", 501, TOKEN))
                    .thenReturn(doc);

            ToolResponse result = executor.documentGetDetails(
                    "PurchaseOrder", 501, TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            assertThat(result.getTool()).isEqualTo("document.get_details");
        }

        @Test
        void invalidIdReturnsToolError() {
            ToolResponse result = executor.documentGetDetails("SalesOrder", 0, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
        }

        @Test
        void notFoundIsMapped() {
            when(client.getEntityDetails("SalesOrder", 999, TOKEN))
                    .thenThrow(new AtracioBackendException(404,
                            Map.of("message", "SalesOrder 999 not found.")));

            ToolResponse result = executor.documentGetDetails("SalesOrder", 999, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("not_found");
        }
    }

    // =========================================================================
    // 3. document.save_draft
    // =========================================================================

    @Nested
    class DocumentSaveDraft {

        @Test
        void happyPath() {
            Map<String, Object> savedDoc = Map.of("id", 44, "subject", "Quote for ACME");

            when(client.saveEntity(eq("Quotation"), any(), eq(TOKEN)))
                    .thenReturn(successEnvelope(savedDoc));

            ToolResponse result = executor.documentSaveDraft(
                    "Quotation",
                    Map.of("subject", "Quote for ACME", "client", Map.of("id", 44)),
                    null, TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            assertThat(result.getTool()).isEqualTo("document.save_draft");
        }

        @Test
        void emptyDocumentReturnsToolError() {
            ToolResponse result = executor.documentSaveDraft(
                    "Quotation", Map.of(), null, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
        }

        @Test
        void validationErrorFromBackendIsMapped() {
            when(client.saveEntity(eq("Quotation"), any(), eq(TOKEN)))
                    .thenReturn(errorEnvelope(List.of(Map.of("completeErrorMessage", "'Currency' must not be null."))));

            ToolResponse result = executor.documentSaveDraft(
                    "Quotation",
                    Map.of("subject", "No client"),
                    null, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("validation_error");
            assertThat(result.getError().message()).isEqualTo("'Currency' must not be null.");
        }
    }

    // =========================================================================
    // 4. document.apply_process_action
    // =========================================================================

    @Nested
    class DocumentApplyProcessAction {

        @Test
        void happyPath() {
            when(client.applyProcessAction(eq("SalesOrder"), eq(101L),
                    eq("lifecycle.release"), isNull(), eq(TOKEN)))
                    .thenReturn(
                            Map.of("id", 101, "lifecycle",
                                    Map.of("lifecycleState", "RELEASED")));

            ToolResponse result = executor.documentApplyProcessAction(
                    "SalesOrder", 101, "lifecycle.release", null, TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            assertThat(result.getTool()).isEqualTo("document.apply_process_action");
        }

        @Test
        void unknownActionFamilyReturnsToolError() {
            ToolResponse result = executor.documentApplyProcessAction(
                    "SalesOrder", 101, "ship.now", null, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
            assertThat(result.getError().message()).contains("Unknown action family");
        }

        @Test
        void invalidActionFormatReturnsToolError() {
            ToolResponse result = executor.documentApplyProcessAction(
                    "SalesOrder", 101, "release", null, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
        }
    }

    // =========================================================================
    // 5. wms.get_article_stock_summary
    // =========================================================================

    @Nested
    class WmsGetArticleStockSummary {

        @Test
        void happyPath() {
            when(client.getArticleQuantity(9001L, 3L, TOKEN)).thenReturn(120.0);
            when(client.getArticleForecast(9001L, 3L, TOKEN)).thenReturn(140.0);
            when(client.getArticleValuation(9001L, TOKEN)).thenReturn(2500.5);
            when(client.getArticleEntries(9001L, TOKEN)).thenReturn(320.0);
            when(client.getArticleIssues(9001L, TOKEN)).thenReturn(200.0);
            when(client.getArticleTurnover(9001L, TOKEN)).thenReturn(18000.0);

            ToolResponse result = executor.wmsGetArticleStockSummary(9001L, 3L, TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            assertThat(result.getTool()).isEqualTo("wms.get_article_stock_summary");
            assertThat(result.getMeta()).containsEntry("aggregated", true);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data.get("stockQuantity")).isEqualTo(120.0);
            assertThat(data.get("valuation")).isEqualTo(2500.5);
        }

        @Test
        void partialSubCallFailureStillReturnsResult() {
            when(client.getArticleQuantity(9001L, 3L, TOKEN)).thenReturn(120.0);
            when(client.getArticleForecast(9001L, 3L, TOKEN))
                    .thenThrow(new RuntimeException("Forecast unavailable"));
            when(client.getArticleValuation(9001L, TOKEN)).thenReturn(2500.5);
            when(client.getArticleEntries(9001L, TOKEN)).thenReturn(320.0);
            when(client.getArticleIssues(9001L, TOKEN)).thenReturn(200.0);
            when(client.getArticleTurnover(9001L, TOKEN)).thenReturn(18000.0);

            ToolResponse result = executor.wmsGetArticleStockSummary(9001L, 3L, TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data.get("stockQuantity")).isEqualTo(120.0);
            assertThat(data.get("forecastQuantity")).isNull(); // graceful null
        }

        @Test
        void invalidArticleIdReturnsToolError() {
            ToolResponse result = executor.wmsGetArticleStockSummary(0L, null, TENANT, TOKEN);
            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
        }
    }

    // =========================================================================
    // 6. wms.lookup_inventory_unit
    // =========================================================================

    @Nested
    class WmsLookupInventoryUnit {

        @Test
        void happyPathBarcode() {
            Map<String, Object> unit = Map.of("id", 7001, "location", "A-01-03");

            when(client.lookupInventoryUnit("barcode", "ABC-123", TOKEN))
                    .thenReturn(unit);

            ToolResponse result = executor.wmsLookupInventoryUnit(
                    "barcode", "ABC-123", TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            assertThat(result.getTool()).isEqualTo("wms.lookup_inventory_unit");
        }

        @Test
        void unknownLookupTypeReturnsToolError() {
            ToolResponse result = executor.wmsLookupInventoryUnit(
                    "qrCode", "XYZ", TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
            assertThat(result.getError().message()).contains("qrCode");
        }

        @Test
        void missingValueReturnsToolError() {
            ToolResponse result = executor.wmsLookupInventoryUnit(
                    "barcode", "", TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
        }
    }

    // =========================================================================
    // 7. partner.get_summary
    // =========================================================================

    @Nested
    class PartnerGetSummary {

        @Test
        void happyPathClient() {
            when(client.getClientTurnover(44L, TOKEN)).thenReturn(125000.0);
            when(client.getClientUnpaidAmount(44L, TOKEN)).thenReturn(8500.0);
            when(client.getClientUnpaidInvoices(44L, TOKEN)).thenReturn(List.of());
            when(client.getClientLastInvoiceDate(44L, TOKEN)).thenReturn(1710000000L);
            when(client.getClientSalesOrdersCount(44L, TOKEN)).thenReturn(12);
            when(client.getClientLastSalesOrder(44L, TOKEN))
                    .thenReturn(Map.of("id", 101, "documentNumber", "SO-000101"));

            ToolResponse result = executor.partnerGetSummary("client", 44L, TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            assertThat(result.getTool()).isEqualTo("partner.get_summary");

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data.get("turnover")).isEqualTo(125000.0);
            assertThat(data.get("salesOrdersCount")).isEqualTo(12);
        }

        @Test
        void happyPathVendor() {
            when(client.getVendorTurnover(18L, TOKEN)).thenReturn(87000.0);
            when(client.getVendorUnpaidAmount(18L, TOKEN)).thenReturn(12000.0);
            when(client.getVendorUnpaidInvoices(18L, TOKEN)).thenReturn(List.of());
            when(client.getVendorLastPurchaseOrderDate(18L, TOKEN)).thenReturn(1710000000L);

            ToolResponse result = executor.partnerGetSummary("vendor", 18L, TENANT, TOKEN);

            assertThat(result.isOk()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertThat(data.get("turnover")).isEqualTo(87000.0);
        }

        @Test
        void unknownPartnerTypeReturnsToolError() {
            ToolResponse result = executor.partnerGetSummary("supplier", 1L, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
            assertThat(result.getError().message()).contains("supplier");
        }

        @Test
        void invalidPartnerIdReturnsToolError() {
            ToolResponse result = executor.partnerGetSummary("client", 0L, TENANT, TOKEN);

            assertThat(result.isOk()).isFalse();
            assertThat(result.getError().code()).isEqualTo("tool_mapping_error");
        }
    }
}