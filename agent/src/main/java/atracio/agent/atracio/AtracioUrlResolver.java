package atracio.agent.atracio;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single point of truth for all Atracio API URL construction.
 *
 * Never build URLs by hand elsewhere in the codebase — always go through here.
 * The base URL is driven by the ATRACIO_BASE_URL environment variable so that
 * switching between environments (demo, staging, prod) requires no code change.
 *
 * Default: https://demo.prod.atracio.com/api
 */
@Component
public class AtracioUrlResolver {

    private final String apiBase;

    public AtracioUrlResolver(
            @Value("${atracio.base-url:https://demo.prod.atracio.com}") String baseUrl) {
        // Normalize: strip trailing slash, then append /api
        this.apiBase = baseUrl.stripTrailing().replaceAll("/+$", "") + "/api";
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    /** POST — login and obtain an access token. */
    public String login() {
        return apiBase + "/auth/login";
    }

    /** POST — refresh an expired access token using the refresh-token cookie. */
    public String refresh() {
        return apiBase + "/auth/refresh";
    }

    /** POST — invalidate the current session. */
    public String logout() {
        return apiBase + "/auth/logout";
    }

    // -------------------------------------------------------------------------
    // Generic entity endpoints
    // -------------------------------------------------------------------------

    /** POST /api/entities/list/{entity} */
    public String listEntities(String entity) {
        return apiBase + "/entities/list/" + entity;
    }

    /** GET /api/entities/details/{entity}/{id} */
    public String entityDetails(String entity, long id) {
        return apiBase + "/entities/details/" + entity + "/" + id;
    }

    /** POST /api/entities/save/{entity} */
    public String saveEntity(String entity) {
        return apiBase + "/entities/save/" + entity;
    }

    /** POST /api/entities/validate/{entity} */
    public String validateEntity(String entity) {
        return apiBase + "/entities/validate/" + entity;
    }

    /** DELETE /api/entities/delete/{entity}/{ids} */
    public String deleteEntity(String entity, String ids) {
        return apiBase + "/entities/delete/" + entity + "/" + ids;
    }

    // -------------------------------------------------------------------------
    // Generic process action endpoints
    // -------------------------------------------------------------------------

    /** POST /api/process/lifecycle/{entity}/{id}/{action}
     *  action = release | close | cancel */
    public String lifecycleAction(String entity, long id, String action) {
        return apiBase + "/process/lifecycle/" + entity + "/" + id + "/" + action;
    }

    /** POST /api/process/approval/{entity}/{id}/{action}
     *  action = submit | approve */
    public String approvalAction(String entity, long id, String action) {
        return apiBase + "/process/approval/" + entity + "/" + id + "/" + action;
    }

    /** POST /api/process/posting/{entity}/{id}/{action}
     *  action = post | reverse */
    public String postingAction(String entity, long id, String action) {
        return apiBase + "/process/posting/" + entity + "/" + id + "/" + action;
    }

    /** POST /api/process/execution/{entity}/{id}/{action}
     *  action = start | complete */
    public String executionAction(String entity, long id, String action) {
        return apiBase + "/process/execution/" + entity + "/" + id + "/" + action;
    }

    /** POST /api/process/payment/{entity}/{id}/{action}
     *  action = allocate */
    public String paymentAction(String entity, long id, String action) {
        return apiBase + "/process/payment/" + entity + "/" + id + "/" + action;
    }

    /**
     * Resolves any process action string from the tool contract (e.g. "lifecycle.release")
     * to its full Atracio API URL.
     *
     * Supported action families: lifecycle, approval, posting, execution, payment.
     *
     * @throws IllegalArgumentException if the action string is unrecognised
     */
    public String resolveProcessAction(String entity, long id, String action) {
        if (action == null || !action.contains(".")) {
            throw new IllegalArgumentException("Invalid action format: '" + action
                    + "'. Expected format: 'family.action' (e.g. 'lifecycle.release').");
        }

        String[] parts  = action.split("\\.", 2);
        String   family = parts[0].toLowerCase();
        String   verb   = parts[1].toLowerCase();

        return switch (family) {
            case "lifecycle" -> lifecycleAction(entity, id, verb);
            case "approval"  -> approvalAction(entity, id, verb);
            case "posting"   -> postingAction(entity, id, verb);
            case "execution" -> executionAction(entity, id, verb);
            case "payment"   -> paymentAction(entity, id, verb);
            default -> throw new IllegalArgumentException(
                    "Unknown action family: '" + family + "' in action '" + action + "'.");
        };
    }

    // -------------------------------------------------------------------------
    // WMS / Warehouse endpoints
    // -------------------------------------------------------------------------

    /** GET /api/warehouse/units/barcode?barcode={value} */
    public String unitsByBarcode(String barcode) {
        return apiBase + "/warehouse/units/barcode?barcode=" + barcode;
    }

    /** GET /api/warehouse/units/rfidTag?rfidTag={value} */
    public String unitsByRfidTag(String rfidTag) {
        return apiBase + "/warehouse/units/rfidTag?rfidTag=" + rfidTag;
    }

    /** GET /api/warehouse/units/serialNumber?serialNumber={value} */
    public String unitsBySerialNumber(String serialNumber) {
        return apiBase + "/warehouse/units/serialNumber?serialNumber=" + serialNumber;
    }

    /** GET /api/warehouse/article/quantity/{articleId}?siteId={siteId} */
    public String articleQuantity(long articleId, Long siteId) {
        String url = apiBase + "/warehouse/article/quantity/" + articleId;
        return siteId != null ? url + "?siteId=" + siteId : url;
    }

    /** GET /api/warehouse/article/quantity/{articleId}/forecast?siteId={siteId} */
    public String articleForecast(long articleId, Long siteId) {
        String url = apiBase + "/warehouse/article/quantity/" + articleId + "/forecast";
        return siteId != null ? url + "?siteId=" + siteId : url;
    }

    /** GET /api/warehouse/article/valuation/{articleId} */
    public String articleValuation(long articleId) {
        return apiBase + "/warehouse/article/valuation/" + articleId;
    }

    /** GET /api/warehouse/article/entries/{articleId} */
    public String articleEntries(long articleId) {
        return apiBase + "/warehouse/article/entries/" + articleId;
    }

    /** GET /api/warehouse/article/issues/{articleId} */
    public String articleIssues(long articleId) {
        return apiBase + "/warehouse/article/issues/" + articleId;
    }

    /** GET /api/warehouse/article/turnover/{articleId} */
    public String articleTurnover(long articleId) {
        return apiBase + "/warehouse/article/turnover/" + articleId;
    }

    /** GET /api/warehouse/article/stock-evolution/{articleId} */
    public String articleStockEvolution(long articleId) {
        return apiBase + "/warehouse/article/stock-evolution/" + articleId;
    }

    // -------------------------------------------------------------------------
    // Client endpoints
    // -------------------------------------------------------------------------

    public String clientTurnover(long clientId) {
        return apiBase + "/client/turnover/" + clientId;
    }

    public String clientUnpaidAmount(long clientId) {
        return apiBase + "/client/unpaid-amount/" + clientId;
    }

    public String clientUnpaidInvoices(long clientId) {
        return apiBase + "/client/unpaid-invoices/" + clientId;
    }

    public String clientLastInvoiceDate(long clientId) {
        return apiBase + "/client/last-invoice-date/" + clientId;
    }

    public String clientSalesOrdersCount(long clientId) {
        return apiBase + "/client/sales-orders/" + clientId + "/count";
    }

    public String clientLastSalesOrder(long clientId) {
        return apiBase + "/client/sales-orders/" + clientId + "/last";
    }

    // -------------------------------------------------------------------------
    // Vendor endpoints
    // -------------------------------------------------------------------------

    public String vendorTurnover(long vendorId) {
        return apiBase + "/vendor/details/turnover/" + vendorId;
    }

    public String vendorUnpaidAmount(long vendorId) {
        return apiBase + "/vendor/details/unpaid-amount/" + vendorId;
    }

    public String vendorUnpaidInvoices(long vendorId) {
        return apiBase + "/vendor/details/unpaid-purchase-invoices/" + vendorId;
    }

    public String vendorLastPurchaseOrderDate(long vendorId) {
        return apiBase + "/vendor/details/last-purchase-order-date/" + vendorId;
    }

    // -------------------------------------------------------------------------
    // Diagnostic
    // -------------------------------------------------------------------------

    /** Returns the resolved API base URL — useful for logging and health checks. */
    public String getApiBase() {
        return apiBase;
    }
}