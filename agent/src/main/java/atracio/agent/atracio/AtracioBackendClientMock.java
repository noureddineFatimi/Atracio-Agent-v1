package atracio.agent.atracio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
 
import java.util.List;
import java.util.Map;
 
/**
 * Mock implementation of AtracioBackendClient.
 *
 * Active when the Spring profile "mock" is set (default for local dev).
 * Returns realistic static responses that mirror the Atracio backend envelope:
 *
 *   { "status": "success", "response": { ... } }
 *
 * No HTTP calls are made. This lets every tool, orchestrator, and test run
 * without a live Atracio environment.
 *
 * Phase 5: replaced by AtracioBackendClientHttp under profile "http".
 *
 * How to activate:
 *   SPRING_PROFILES_ACTIVE=mock  (or set in application.yml: spring.profiles.active: mock)
 */
@Component
@Profile("mock")
public class AtracioBackendClientMock implements AtracioBackendClient {
 
    private static final Logger log = LoggerFactory.getLogger(AtracioBackendClientMock.class);
 
    // -------------------------------------------------------------------------
    // Generic entity operations
    // -------------------------------------------------------------------------
 
    @Override
    public Map<String, Object> listEntities(String entity,
                                            Map<String, Object> requestBody,
                                            String bearerToken) {
        log.debug("[MOCK] listEntities entity={} filter={}", entity,
                requestBody.getOrDefault("filter", ""));
 
        return success(Map.of(
                "content", List.of(
                        Map.of(
                                "id", 101,
                                "documentNumber", entity.substring(0, 2).toUpperCase() + "-000101",
                                "subject", "Mock document — " + entity,
                                "lifecycle", Map.of("lifecycleState", "DRAFT"),
                                "client", Map.of("id", 44, "name", "ACME Corp"),
                                "vendor", Map.of("id", 18, "name", "Global Vendor")
                        ),
                        Map.of(
                                "id", 102,
                                "documentNumber", entity.substring(0, 2).toUpperCase() + "-000102",
                                "subject", "Mock document 2 — " + entity,
                                "lifecycle", Map.of("lifecycleState", "RELEASED"),
                                "client", Map.of("id", 44, "name", "ACME Corp"),
                                "vendor", Map.of("id", 18, "name", "Global Vendor")
                        )
                ),
                "pageable", Map.of(
                        "pageNumber", 0,
                        "pageSize", 20
                ),
                "totalElements", 2,
                "totalPages", 1
        ));
    }
 
    @Override
    public Map<String, Object> getEntityDetails(String entity,
                                                long id,
                                                String bearerToken) {
        log.debug("[MOCK] getEntityDetails entity={} id={}", entity, id);
 
        return success(Map.of(
                "id", id,
                "documentNumber", entity.substring(0, 2).toUpperCase() + "-" + String.format("%06d", id),
                "subject", "Mock document details — " + entity + " #" + id,
                "lifecycle", Map.of("lifecycleState", "DRAFT"),
                "client", Map.of("id", 44, "name", "ACME Corp"),
                "vendor", Map.of("id", 18, "name", "Global Vendor"),
                "lines", List.of(
                        Map.of("id", 1, "rowNumber", 1,
                                "article", Map.of("id", 9001, "code", "ART-001", "name", "Widget A"),
                                "quantity", 10,
                                "unitPrice", 25.0)
                )
        ));
    }
 
    @Override
    public Map<String, Object> saveEntity(String entity,
                                          Map<String, Object> requestBody,
                                          String bearerToken) {
        log.debug("[MOCK] saveEntity entity={}", entity);
 
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) requestBody.getOrDefault("entity", Map.of());
        Object id = doc.getOrDefault("id", 999);
 
        return success(Map.of(
                "id", id,
                "documentNumber", entity.substring(0, 2).toUpperCase() + "-" + String.format("%06d", id),
                "subject", doc.getOrDefault("subject", "Saved document"),
                "lifecycle", Map.of("lifecycleState", "DRAFT")
        ));
    }
 
    // -------------------------------------------------------------------------
    // Process actions
    // -------------------------------------------------------------------------
 
    @Override
    public Map<String, Object> applyProcessAction(String entity,
                                                  long id,
                                                  String action,
                                                  Map<String, Object> payload,
                                                  String bearerToken) {
        log.debug("[MOCK] applyProcessAction entity={} id={} action={}", entity, id, action);
 
        String newState = switch (action.toLowerCase()) {
            case "lifecycle.release"    -> "RELEASED";
            case "lifecycle.close"      -> "CLOSED";
            case "lifecycle.cancel"     -> "CANCELLED";
            case "approval.submit"      -> "PENDING_APPROVAL";
            case "approval.approve"     -> "APPROVED";
            case "posting.post"         -> "POSTED";
            case "posting.reverse"      -> "REVERSED";
            case "execution.start"      -> "IN_PROGRESS";
            case "execution.complete"   -> "COMPLETED";
            case "payment.allocate"     -> "ALLOCATED";
            default                     -> "UNKNOWN";
        };
 
        return success(Map.of(
                "id", id,
                "entity", entity,
                "action", action,
                "lifecycle", Map.of("lifecycleState", newState)
        ));
    }
 
    // -------------------------------------------------------------------------
    // WMS — article stock
    // -------------------------------------------------------------------------
 
    @Override
    public Map<String, Object> getArticleQuantity(long articleId,
                                                  Long siteId,
                                                  String bearerToken) {
        log.debug("[MOCK] getArticleQuantity articleId={} siteId={}", articleId, siteId);
        return success(Map.of("articleId", articleId, "siteId", siteId, "quantity", 120.0));
    }
 
    @Override
    public Map<String, Object> getArticleForecast(long articleId,
                                                   Long siteId,
                                                   String bearerToken) {
        log.debug("[MOCK] getArticleForecast articleId={} siteId={}", articleId, siteId);
        return success(Map.of("articleId", articleId, "siteId", siteId, "forecastQuantity", 140.0));
    }
 
    @Override
    public Map<String, Object> getArticleValuation(long articleId, String bearerToken) {
        log.debug("[MOCK] getArticleValuation articleId={}", articleId);
        return success(Map.of("articleId", articleId, "valuation", 2500.50));
    }
 
    @Override
    public Map<String, Object> getArticleEntries(long articleId, String bearerToken) {
        log.debug("[MOCK] getArticleEntries articleId={}", articleId);
        return success(Map.of("articleId", articleId, "entriesCurrentYear", 320));
    }
 
    @Override
    public Map<String, Object> getArticleIssues(long articleId, String bearerToken) {
        log.debug("[MOCK] getArticleIssues articleId={}", articleId);
        return success(Map.of("articleId", articleId, "issuesCurrentYear", 200));
    }
 
    @Override
    public Map<String, Object> getArticleTurnover(long articleId, String bearerToken) {
        log.debug("[MOCK] getArticleTurnover articleId={}", articleId);
        return success(Map.of("articleId", articleId, "turnover", 18000.0, "ordersQuantity", 210));
    }
 
    // -------------------------------------------------------------------------
    // WMS — inventory unit lookup
    // -------------------------------------------------------------------------
 
    @Override
    public Map<String, Object> lookupInventoryUnit(String lookupType,
                                                   String value,
                                                   String bearerToken) {
        log.debug("[MOCK] lookupInventoryUnit type={} value={}", lookupType, value);
        return success(Map.of(
                "id", 7001,
                "lookupType", lookupType,
                "value", value,
                "article", Map.of("id", 9001, "code", "ART-001", "name", "Widget A"),
                "warehouse", Map.of("id", 2, "name", "Main Warehouse"),
                "location", "A-01-03",
                "quantity", 15.0
        ));
    }
 
    // -------------------------------------------------------------------------
    // Partner — client
    // -------------------------------------------------------------------------
 
    @Override
    public Map<String, Object> getClientTurnover(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientTurnover clientId={}", clientId);
        return success(Map.of("clientId", clientId, "turnover", 125000.0));
    }
 
    @Override
    public Map<String, Object> getClientUnpaidAmount(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientUnpaidAmount clientId={}", clientId);
        return success(Map.of("clientId", clientId, "unpaidAmount", 8500.0));
    }
 
    @Override
    public Map<String, Object> getClientUnpaidInvoices(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientUnpaidInvoices clientId={}", clientId);
        return success(Map.of(
                "clientId", clientId,
                "invoices", List.of(
                        Map.of("id", 201, "documentNumber", "INV-000201",
                                "amount", 5000.0, "dueDate", "2026-04-30"),
                        Map.of("id", 202, "documentNumber", "INV-000202",
                                "amount", 3500.0, "dueDate", "2026-05-15")
                )
        ));
    }
 
    @Override
    public Map<String, Object> getClientLastInvoiceDate(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientLastInvoiceDate clientId={}", clientId);
        return success(Map.of("clientId", clientId, "lastInvoiceDate", "2026-03-15"));
    }
 
    @Override
    public Map<String, Object> getClientSalesOrdersCount(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientSalesOrdersCount clientId={}", clientId);
        return success(Map.of("clientId", clientId, "count", 12));
    }
 
    @Override
    public Map<String, Object> getClientLastSalesOrder(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientLastSalesOrder clientId={}", clientId);
        return success(Map.of(
                "clientId", clientId,
                "id", 101,
                "documentNumber", "SO-000101",
                "date", "2026-04-01",
                "lifecycle", Map.of("lifecycleState", "RELEASED")
        ));
    }
 
    // -------------------------------------------------------------------------
    // Partner — vendor
    // -------------------------------------------------------------------------
 
    @Override
    public Map<String, Object> getVendorTurnover(long vendorId, String bearerToken) {
        log.debug("[MOCK] getVendorTurnover vendorId={}", vendorId);
        return success(Map.of("vendorId", vendorId, "turnover", 87000.0));
    }
 
    @Override
    public Map<String, Object> getVendorUnpaidAmount(long vendorId, String bearerToken) {
        log.debug("[MOCK] getVendorUnpaidAmount vendorId={}", vendorId);
        return success(Map.of("vendorId", vendorId, "unpaidAmount", 12000.0));
    }
 
    @Override
    public Map<String, Object> getVendorUnpaidInvoices(long vendorId, String bearerToken) {
        log.debug("[MOCK] getVendorUnpaidInvoices vendorId={}", vendorId);
        return success(Map.of(
                "vendorId", vendorId,
                "invoices", List.of(
                        Map.of("id", 301, "documentNumber", "PINV-000301",
                                "amount", 12000.0, "dueDate", "2026-05-01")
                )
        ));
    }
 
    @Override
    public Map<String, Object> getVendorLastPurchaseOrderDate(long vendorId, String bearerToken) {
        log.debug("[MOCK] getVendorLastPurchaseOrderDate vendorId={}", vendorId);
        return success(Map.of("vendorId", vendorId, "lastPurchaseOrderDate", "2026-03-28"));
    }
 
    // -------------------------------------------------------------------------
    // Internal helper — wraps response in the Atracio success envelope
    // -------------------------------------------------------------------------
 
    private Map<String, Object> success(Object response) {
        return Map.of(
                "status", "success",
                "response", response
        );
    }
}