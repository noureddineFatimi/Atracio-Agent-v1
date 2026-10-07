package atracio.agent.atracio;


import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtracioUrlResolverTest {

    private final AtracioUrlResolver resolver =
            new AtracioUrlResolver("https://url");

    // -------------------------------------------------------------------------
    // Base URL normalisation
    // -------------------------------------------------------------------------

    @Test
    void shouldNormalizeBaseUrlWithTrailingSlash() {
        AtracioUrlResolver r = new AtracioUrlResolver("https://url/");
        assertThat(r.getApiBase()).isEqualTo("https://url/api");
    }

    @Test
    void shouldNormalizeBaseUrlWithMultipleTrailingSlashes() {
        AtracioUrlResolver r = new AtracioUrlResolver("https://url///");
        assertThat(r.getApiBase()).isEqualTo("https://url/api");
    }

    @Test
    void shouldBuildApiBaseCorrectly() {
        assertThat(resolver.getApiBase()).isEqualTo("https://url/api");
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    @Nested
    class Auth {
        @Test
        void login() {
            assertThat(resolver.login())
                    .isEqualTo("https://url/api/auth/login");
        }

        @Test
        void refresh() {
            assertThat(resolver.refresh())
                    .isEqualTo("https://url/api/auth/refresh");
        }

        @Test
        void logout() {
            assertThat(resolver.logout())
                    .isEqualTo("https://url/api/auth/logout");
        }
    }

    // -------------------------------------------------------------------------
    // Generic entity endpoints
    // -------------------------------------------------------------------------

    @Nested
    class Entities {
        @Test
        void listEntities() {
            assertThat(resolver.listEntities("SalesOrder"))
                    .isEqualTo("https://url/api/entities/list/SalesOrder");
        }

        @Test
        void entityDetails() {
            assertThat(resolver.entityDetails("PurchaseOrder", 501))
                    .isEqualTo("https://url/api/entities/details/PurchaseOrder/501");
        }

        @Test
        void saveEntity() {
            assertThat(resolver.saveEntity("Quotation"))
                    .isEqualTo("https://url/api/entities/save/Quotation");
        }

        @Test
        void validateEntity() {
            assertThat(resolver.validateEntity("Invoice"))
                    .isEqualTo("https://url/api/entities/validate/Invoice");
        }

        @Test
        void deleteEntity() {
            assertThat(resolver.deleteEntity("StockAdjustment", "12,13,14"))
                    .isEqualTo("https://url/api/entities/delete/StockAdjustment/12,13,14");
        }
    }

    // -------------------------------------------------------------------------
    // Process actions — individual builders
    // -------------------------------------------------------------------------

    @Nested
    class ProcessActions {
        @Test
        void lifecycleAction() {
            assertThat(resolver.lifecycleAction("SalesOrder", 101, "release"))
                    .isEqualTo("https://url/api/process/lifecycle/SalesOrder/101/release");
        }

        @Test
        void approvalAction() {
            assertThat(resolver.approvalAction("PurchaseOrder", 202, "approve"))
                    .isEqualTo("https://url/api/process/approval/PurchaseOrder/202/approve");
        }

        @Test
        void postingAction() {
            assertThat(resolver.postingAction("PurchaseInvoice", 303, "post"))
                    .isEqualTo("https://url/api/process/posting/PurchaseInvoice/303/post");
        }

        @Test
        void executionAction() {
            assertThat(resolver.executionAction("StockTransferOrder", 404, "start"))
                    .isEqualTo("https://url/api/process/execution/StockTransferOrder/404/start");
        }

        @Test
        void paymentAction() {
            assertThat(resolver.paymentAction("Invoice", 505, "allocate"))
                    .isEqualTo("https://url/api/process/payment/Invoice/505/allocate");
        }
    }

    // -------------------------------------------------------------------------
    // resolveProcessAction — the tool contract dispatcher
    // -------------------------------------------------------------------------

    @Nested
    class ResolveProcessAction {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "lifecycle.release, /api/process/lifecycle/SalesOrder/101/release",
            "lifecycle.close,   /api/process/lifecycle/SalesOrder/101/close",
            "lifecycle.cancel,  /api/process/lifecycle/SalesOrder/101/cancel",
            "approval.submit,   /api/process/approval/SalesOrder/101/submit",
            "approval.approve,  /api/process/approval/SalesOrder/101/approve",
            "posting.post,      /api/process/posting/SalesOrder/101/post",
            "posting.reverse,   /api/process/posting/SalesOrder/101/reverse",
            "execution.start,   /api/process/execution/SalesOrder/101/start",
            "execution.complete,/api/process/execution/SalesOrder/101/complete",
            "payment.allocate,  /api/process/payment/SalesOrder/101/allocate",
        })
        void shouldResolveAllSupportedActions(String action, String expectedPath) {
            String url = resolver.resolveProcessAction("SalesOrder", 101, action.trim());
            assertThat(url).isEqualTo("https://url" + expectedPath.trim());
        }

        @Test
        void shouldThrowOnUnknownFamily() {
            assertThatThrownBy(() -> resolver.resolveProcessAction("SalesOrder", 1, "ship.now"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown action family");
        }

        @Test
        void shouldThrowOnMissingDot() {
            assertThatThrownBy(() -> resolver.resolveProcessAction("SalesOrder", 1, "release"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid action format");
        }

        @Test
        void shouldThrowOnNullAction() {
            assertThatThrownBy(() -> resolver.resolveProcessAction("SalesOrder", 1, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -------------------------------------------------------------------------
    // WMS endpoints
    // -------------------------------------------------------------------------

    @Nested
    class Wms {
        @Test
        void unitsByBarcode() {
            assertThat(resolver.unitsByBarcode("ABC-123"))
                    .isEqualTo("https://url/api/warehouse/units/barcode?barcode=ABC-123");
        }

        @Test
        void unitsByRfidTag() {
            assertThat(resolver.unitsByRfidTag("RFID-99"))
                    .isEqualTo("https://url/api/warehouse/units/rfidTag?rfidTag=RFID-99");
        }

        @Test
        void unitsBySerialNumber() {
            assertThat(resolver.unitsBySerialNumber("SN-001"))
                    .isEqualTo("https://url/api/warehouse/units/serialNumber?serialNumber=SN-001");
        }

        @Test
        void articleQuantityWithSite() {
            assertThat(resolver.articleQuantity(9001L, 3L))
                    .isEqualTo("https://url/api/warehouse/article/quantity/9001?siteId=3");
        }

        @Test
        void articleQuantityWithoutSite() {
            assertThat(resolver.articleQuantity(9001L, null))
                    .isEqualTo("https://url/api/warehouse/article/quantity/9001");
        }

        @Test
        void articleForecastWithSite() {
            assertThat(resolver.articleForecast(9001L, 3L))
                    .isEqualTo("https://url/api/warehouse/article/quantity/9001/forecast?siteId=3");
        }

        @Test
        void articleValuation() {
            assertThat(resolver.articleValuation(9001L))
                    .isEqualTo("https://url/api/warehouse/article/valuation/9001");
        }

        @Test
        void articleTurnover() {
            assertThat(resolver.articleTurnover(9001L))
                    .isEqualTo("https://url/api/warehouse/article/turnover/9001");
        }

        @Test
        void articleStockEvolution() {
            assertThat(resolver.articleStockEvolution(9001L))
                    .isEqualTo("https://url/api/warehouse/article/stock-evolution/9001");
        }
    }

    // -------------------------------------------------------------------------
    // Client / Vendor endpoints
    // -------------------------------------------------------------------------

    @Nested
    class Partners {
        @Test
        void clientTurnover() {
            assertThat(resolver.clientTurnover(44L))
                    .isEqualTo("https://url/api/client/turnover/44");
        }

        @Test
        void clientUnpaidInvoices() {
            assertThat(resolver.clientUnpaidInvoices(44L))
                    .isEqualTo("https://url/api/client/unpaid-invoices/44");
        }

        @Test
        void clientSalesOrdersCount() {
            assertThat(resolver.clientSalesOrdersCount(44L))
                    .isEqualTo("https://url/api/client/sales-orders/44/count");
        }

        @Test
        void clientLastSalesOrder() {
            assertThat(resolver.clientLastSalesOrder(44L))
                    .isEqualTo("https://url/api/client/sales-orders/44/last");
        }

        @Test
        void vendorTurnover() {
            assertThat(resolver.vendorTurnover(18L))
                    .isEqualTo("https://url/api/vendor/details/turnover/18");
        }

        @Test
        void vendorUnpaidInvoices() {
            assertThat(resolver.vendorUnpaidInvoices(18L))
                    .isEqualTo("https://url/api/vendor/details/unpaid-purchase-invoices/18");
        }

        @Test
        void vendorLastPurchaseOrderDate() {
            assertThat(resolver.vendorLastPurchaseOrderDate(18L))
                    .isEqualTo("https://url/api/vendor/details/last-purchase-order-date/18");
        }
    }
}