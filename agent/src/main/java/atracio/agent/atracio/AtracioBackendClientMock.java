package atracio.agent.atracio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.Math;
 
/**
 * Mock implementation of AtracioBackendClient.
 *
 * Active when the Spring profile "mock" is set (default for local dev).
 * Returns realistic static responses that mirror the Atracio backend envelope:
 *
 *   (e.g.{ "status": "success", "response": { ... } })
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
 
        return Map.of(
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
        );
    }
 
    @Override
    public Map<String, Object> getEntityDetails(String entity,
                                                long id,
                                                String bearerToken) {
        log.debug("[MOCK] getEntityDetails entity={} id={}", entity, id);
 
        return Map.of(
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
        );
    }
 
    @Override
    public Map<String, Object> saveEntity(String entity,
                                          Map<String, Object> requestBody,
                                          String bearerToken) {
        log.debug("[MOCK] saveEntity entity={}", entity);
 
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) requestBody.getOrDefault("entity", Map.of());
        int id = (int) ((Math.random() * (700000 - 600000)) + 600000);                                    
        return success(Map.of(
                "id", id,
                "documentNumber", entity.substring(0, 2).toUpperCase() + "-" + String.format("%06d", id),
                "subject", doc.getOrDefault("subject", "Saved document"),
                "lifecycle", Map.of("lifecycleState", "DRAFT")
        ));
    }

    @Override
    public List<Map<String, Object>> validateEntity(String entity,
                                          Map<String, Object> requestBody,
                                          String bearerToken) {
        log.debug("[MOCK] saveEntity entity={}", entity);

        List<Map<String, Object>> success_empty_list = new ArrayList<>();
        return success_empty_list;
    }
    
    @Override
    public Map<String, Object> deleteEntity(String entity,
                                            String ids,
                                            String bearerToken) {
        log.debug("[MOCK] saveEntity entity={}", entity);

        return success(null);
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
 
        return Map.of(
                "entity", entity,
                "id", id,
                "state",newState
        );
    }
 
    // -------------------------------------------------------------------------
    // WMS — article stock
    // -------------------------------------------------------------------------
 
    @Override
    public Double getArticleQuantity(long articleId,
                                                  Long siteId,
                                                  String bearerToken) {
        log.debug("[MOCK] getArticleQuantity articleId={} siteId={}", articleId, siteId);
        return (double)120.0;
    }
 
    @Override
    public Double getArticleForecast(long articleId,
                                                   Long siteId,
                                                   String bearerToken) {
        log.debug("[MOCK] getArticleForecast articleId={} siteId={}", articleId, siteId);
        return (double)140.0;
    }
 
    @Override
    public Double getArticleValuation(long articleId, String bearerToken) {
        log.debug("[MOCK] getArticleValuation articleId={}", articleId);
        return (double)2500.50;
    }
 
    @Override
    public Double getArticleEntries(long articleId, String bearerToken) {
        log.debug("[MOCK] getArticleEntries articleId={}", articleId);
        return (double)320;
    }
 
    @Override
    public Double getArticleIssues(long articleId, String bearerToken) {
        log.debug("[MOCK] getArticleIssues articleId={}", articleId);
        return (double)200;
    }
 
    @Override
    public Double getArticleTurnover(long articleId, String bearerToken) {
        log.debug("[MOCK] getArticleTurnover articleId={}", articleId);
        return (double)210;
    }
    
    @Override
    public List<Map<String, Object>> getStockEvolution(long articleId, String bearerToken){
        Map<String, Object> stock_evolution_dict_1 = Map.of( "date", 1775088000000L, "value", 13.000000);
        Map<String, Object> stock_evolution_dict_2 = Map.of( "date", 1775520000000L, "value", 13.000000);
        Map<String, Object> stock_evolution_dict_3 = Map.of( "date", 1775606400000L, "value", 13.000000);

        List<Map<String, Object>> stock_evolution_list = new ArrayList<>();

        stock_evolution_list.add(stock_evolution_dict_1);
        stock_evolution_list.add(stock_evolution_dict_2);
        stock_evolution_list.add(stock_evolution_dict_3);

        return stock_evolution_list;                    

    }

    // -------------------------------------------------------------------------
    // WMS — inventory unit lookup
    // -------------------------------------------------------------------------
 
    @Override
    public Map<String, Object> lookupInventoryUnit(String lookupType,
                                                   String value,
                                                   String bearerToken) {
        log.debug("[MOCK] lookupInventoryUnit type={} value={}", lookupType, value);
        return Map.of(
                "id", 7001,
                "lookupType", lookupType,
                "value", value,
                "article", Map.of("id", 9001, "code", "ART-001", "name", "Widget A"),
                "warehouse", Map.of("id", 2, "name", "Main Warehouse"),
                "location", "A-01-03",
                "quantity", 15.0
        );
    }
 
    // -------------------------------------------------------------------------
    // Partner — client
    // -------------------------------------------------------------------------
 
    @Override
    public Double getClientTurnover(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientTurnover clientId={}", clientId);
        return (double)125000.0;
    }
 
    @Override
    public Double getClientUnpaidAmount(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientUnpaidAmount clientId={}", clientId);
        return (double)8500.0;
    }
 
    @Override
    public List<Map<String, Object>> getClientUnpaidInvoices(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientUnpaidInvoices clientId={}", clientId);
        List<Map<String, Object>> unpaid_invoices_list = new ArrayList<>();
        Map<String, Object> unpaid_invoice = Map.of(
                "clientId", clientId,
                "invoices", List.of(
                        Map.of("id", 201, "documentNumber", "INV-000201",
                                "amount", 5000.0, "dueDate", "2026-04-30"),
                        Map.of("id", 202, "documentNumber", "INV-000202",
                                "amount", 3500.0, "dueDate", "2026-05-15")
                )
        );
        unpaid_invoices_list.add(unpaid_invoice);
        return unpaid_invoices_list;
    }
 
    @Override
    public Long getClientLastInvoiceDate(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientLastInvoiceDate clientId={}", clientId);
        return 1774828800000L;
    }
 
    @Override
    public Integer getClientSalesOrdersCount(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientSalesOrdersCount clientId={}", clientId);
        return 12;
    }
 
    @Override
    public Map<String, Object> getClientLastSalesOrder(long clientId, String bearerToken) {
        log.debug("[MOCK] getClientLastSalesOrder clientId={}", clientId);
        return Map.of(
                "clientId", clientId,
                "id", 101,
                "documentNumber", "SO-000101",
                "date", "2026-04-01",
                "lifecycle", Map.of("lifecycleState", "RELEASED")
        );
    }
 
    // -------------------------------------------------------------------------
    // Partner — vendor
    // -------------------------------------------------------------------------
 
    @Override
    public Double getVendorTurnover(long vendorId, String bearerToken) {
        log.debug("[MOCK] getVendorTurnover vendorId={}", vendorId);
        return 87000.0;
    }
 
    @Override
    public Double getVendorUnpaidAmount(long vendorId, String bearerToken) {
        log.debug("[MOCK] getVendorUnpaidAmount vendorId={}", vendorId);
        return 12000.0;
    }
 
    @Override
    public List<Map<String, Object>> getVendorUnpaidInvoices(long vendorId, String bearerToken) {
        log.debug("[MOCK] getVendorUnpaidInvoices vendorId={}", vendorId);
        List<Map<String, Object>> unpaid_invoices_list = new ArrayList<>();
        Map<String, Object> unpaid_invoice = Map.of(
                "vendorId", vendorId,
                "invoices", List.of(
                        Map.of("id", 301, "documentNumber", "PINV-000301",
                                "amount", 12000.0, "dueDate", "2026-05-01")
                )
        );
        unpaid_invoices_list.add(unpaid_invoice);
        return unpaid_invoices_list;
    }
 
    @Override
    public Long getVendorLastPurchaseOrderDate(long vendorId, String bearerToken) {
        log.debug("[MOCK] getVendorLastPurchaseOrderDate vendorId={}", vendorId);
        return 1774828800000L;
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