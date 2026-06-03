package atracio.agent.tools;

import atracio.agent.atracio.AtracioBackendClient;
import atracio.agent.atracio.AtracioBackendException;
import atracio.agent.atracio.AtracioErrorMapper;
import atracio.agent.atracio.AtracioTokenExpiredException;
import atracio.agent.atracio.AtracioErrorMapper.NormalisedError;
import atracio.agent.atracio.AtracioUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes all 7 tools defined in the guide (section 15).
 *
 * Every tool follows the same pattern:
 *
 *   1. Validate inputs — return toolError immediately if invalid (no backend call)
 *   2. Call AtracioBackendClient
 *   3. If raw response has status=error → mapBusinessError
 *   4. If AtracioBackendException is caught → map(httpStatus, body)
 *   5. If any other exception is caught → mapException
 *   6. On success → extract "response" from the raw body → ToolResponse.success
 *
 * The LLM and the orchestrator only ever see ToolResponse — never raw Atracio data.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final AtracioBackendClient client;
    private final AtracioErrorMapper   errorMapper;
    private final AtracioUrlResolver   urlResolver;

    public ToolExecutor(AtracioBackendClient client,
                        AtracioErrorMapper errorMapper,
                        AtracioUrlResolver urlResolver) {
        this.client      = client;
        this.errorMapper = errorMapper;
        this.urlResolver = urlResolver;
    }

    // =========================================================================
    // 1. document.search
    // POST /api/entities/list/{entity}
    // =========================================================================

    /**
     * Search and list documents generically across WMS, Sales, and Procurement.
     *
     * @param entity        Atracio entity key (e.g. "SalesOrder")
     * @param filter        optional free-text search string
     * @param page          page number (0-based)
     * @param size          page size
     * @param sort          sort directives (e.g. ["documentNumber,desc"])
     * @param entityFilters optional structured filters map
     * @param fieldsToFetch optional list of fields to include in the response
     * @param tenant        tenant identifier
     * @param bearerToken   user access token
     */
    public ToolResponse documentSearch(String entity,
                                       String filter,
                                       int page,
                                       int size,
                                       List<String> sort,
                                       Map<String, Object> entityFilters,
                                       List<String> fieldsToFetch,
                                       String tenant,
                                       String bearerToken) {

        final String toolName    = "document.search";
        final String backendPath = "/entities/list/" + entity;

        if (entity == null || entity.isBlank()) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'entity' is required.", tenant, backendPath);
        }

        Map<String, Object> filters = translateEntityFilters(entityFilters);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("filter",        filter != null ? filter : "");
            requestBody.put("page",          page >= 0 ? page : 0);
            requestBody.put("size",          size > 0 ? size : 20);
            requestBody.put("sort",          sort != null ? sort : List.of());
            requestBody.put("asPage",        true);
            requestBody.put("entityFilters", filters != null ? filters : Map.of());
            if (fieldsToFetch != null && !fieldsToFetch.isEmpty()) {
                requestBody.put("fieldsToFetch", fieldsToFetch);
            }

            Map<String, Object> raw = client.listEntities(entity, requestBody, bearerToken);

            if (isBusinessError(raw)) {
                return ToolResponse.error(toolName,
                        errorMapper.mapBusinessError(raw), tenant, backendPath);
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) raw.get("content");

            if ("StockReceipt".equals(entity)) {
                List<Map<String, Object>> lines_items = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    Map<String, Object> lines = extractStockReceiptLines(item);
                    lines_items.add(lines);
                }
                items = new ArrayList<>(lines_items);
            }

            Object pageable = raw.get("pageable");
            Map<?, ?> pageableMap  = pageable instanceof Map<?, ?> p ? p : Map.of();
            Object totalElements = raw.get("totalElements");
            Object totalPages = raw.get("totalPages");
            Map<String, Object> data = Map.of(
                    "entity", entity,
                    "items", items,
                    "page", Map.of("number", pageableMap.get("pageNumber") instanceof Integer pN ? pN : 0, "size", pageableMap.get("pageSize") instanceof Integer pS ? pS : 0, "totalElements", totalElements instanceof Integer tE ? tE : 0,"totalPages", totalPages instanceof Integer tP ? tP : 0)
            );

            log.debug("[{}] entity={} filter='{}' ok", toolName, entity, filter);
            return ToolResponse.success(toolName, data, tenant, backendPath);

        } catch (AtracioBackendException ex) {
            NormalisedError err = errorMapper.map(ex.getHttpStatus(), ex.getBody());
            log.warn("[{}] backend error {} {}", toolName, ex.getHttpStatus(), err.code());
            return ToolResponse.error(toolName, err, tenant, backendPath);
        } catch (Exception ex) {
            NormalisedError err = errorMapper.mapException(ex);
            log.error("[{}] unexpected error", toolName, ex);
            return ToolResponse.error(toolName, err, tenant, backendPath);
        }
    }

    private Map<String, Object> translateEntityFilters(Map<String, Object> llmFilters) {
        if (llmFilters == null || !llmFilters.containsKey("conditions")) {
            return llmFilters; // passthrough si format legacy
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) llmFilters.get("conditions");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldFilters = conditions.stream()
            .map(c -> {
                List<String> values = c.containsKey("values")
                    ? (List<String>) c.get("values")
                    : List.of();

                return Map.of(
                    "field",       c.get("field"),
                    "operator",    c.get("operator"),
                    "value",       values
                );
            })
            .collect(Collectors.toList());

        return Map.of(
            "dependenciesOp", "AND",
            "fieldFilters",   fieldFilters
        );
    }

    // =========================================================================
    // 2. document.get_details
    // GET /api/entities/details/{entity}/{id}
    // =========================================================================

    /**
     * Retrieve a single document with its full payload.
     */
    public ToolResponse documentGetDetails(String entity,
                                           long id,
                                           String tenant,
                                           String bearerToken) {

        final String toolName    = "document.get_details";
        final String backendPath = "/entities/details/" + entity + "/" + id;

        if (entity == null || entity.isBlank()) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'entity' is required.", tenant, backendPath);
        }
        if (id <= 0) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'id' not valid.", tenant, backendPath);
        }

        try {
            Map<String, Object> raw = client.getEntityDetails(entity, id, bearerToken);

            if (isNotFoundError(raw)) {
                return ToolResponse.error(toolName,
                        errorMapper.mapNotFoundError(raw), tenant, backendPath);
            }

            if (isBusinessError(raw)) {
                return ToolResponse.error(toolName,
                        errorMapper.mapBusinessError(raw), tenant, backendPath);
            }

            if ("StockReceipt".equals(entity)) {
                raw = extractStockReceiptLines(raw);
            }

            Map<String, Object> data = Map.of(
                    "entity",   entity,
                    "document", raw != null ? raw : Map.of()
            );

            log.debug("[{}] entity={} id={} ok", toolName, entity, id);
            return ToolResponse.success(toolName, data, tenant, backendPath);

        } catch (AtracioBackendException ex) {
            NormalisedError err = errorMapper.map(ex.getHttpStatus(), ex.getBody());
            log.warn("[{}] backend error {} {}", toolName, ex.getHttpStatus(), err.code());
            return ToolResponse.error(toolName, err, tenant, backendPath);
        } catch (Exception ex) {
            NormalisedError err = errorMapper.mapException(ex);
            log.error("[{}] unexpected error", toolName, ex);
            return ToolResponse.error(toolName, err, tenant, backendPath);
        }
    }

    private Map<String, Object> extractStockReceiptLines(Map<String, Object> data) {
        if (data == null) {
            return Map.of();
        }
        Long id = data.get("id") instanceof Long l ? l : data.get("id") instanceof Integer i ? Long.valueOf(i) : 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = data.get("lines") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        List<Map<String, Object>> articles = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            @SuppressWarnings("unchecked")
            Map<String, Object> article = line.get("article") instanceof Map<?, ?> a ? (Map<String, Object>) a : Map.of();
            articles.add(article);
        }
        return Map.of("articles data of stock receipt with id" + id, articles);
    }

    // =========================================================================
    // 3. document.save_draft
    // POST /api/entities/save/{entity}
    // V1 rule: update existing drafts only — fetch first, modify, send back
    // =========================================================================

    /**
     * Create or update a draft document using the generic save API.
     *
     * V1 scope: always fetch the document first with document.get_details,
     * modify only the needed fields, then pass the full object here.
     *
     * @param entity           Atracio entity key
     * @param document         the full document payload to save
     * @param customFieldValues optional custom field values list
     */
    public ToolResponse documentSaveDraft(String entity,
                                          Map<String, Object> document,
                                          List<Map<String, Object>> customFieldValues,
                                          String tenant,
                                          String bearerToken) {

        final String toolName    = "document.save_draft";
        final String backendPath = "/entities/save/" + entity;

        if (entity == null || entity.isBlank()) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'entity' is required.", tenant, backendPath);
        }
        if (document == null || document.isEmpty()) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'document' is required and cannot be empty.", tenant, backendPath);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("entity",           document);
            requestBody.put("customFieldValues", customFieldValues != null
                    ? customFieldValues : List.of());

            Map<String, Object> raw = client.saveEntity(entity, requestBody, bearerToken);

            if (isBusinessError(raw)) {
                return ToolResponse.error(toolName,
                        errorMapper.mapBusinessError(raw), tenant, backendPath);
            }

            Object response = raw.get("response");
            Map<String, Object> data = Map.of(
                    "entity",   entity,
                    "document", response != null ? response : Map.of()
            );

            log.debug("[{}] entity={} ok", toolName, entity);
            return ToolResponse.success(toolName, data, tenant, backendPath);

        } catch (AtracioBackendException ex) {
            NormalisedError err = errorMapper.map(ex.getHttpStatus(), ex.getBody());
            log.warn("[{}] backend error {} {}", toolName, ex.getHttpStatus(), err.code());
            return ToolResponse.error(toolName, err, tenant, backendPath);
        } catch (Exception ex) {
            NormalisedError err = errorMapper.mapException(ex);
            log.error("[{}] unexpected error", toolName, ex);
            return ToolResponse.error(toolName, err, tenant, backendPath);
        }
    }

    // =========================================================================
    // 4. document.apply_process_action
    // POST /api/process/{family}/{entity}/{id}/{verb}
    // =========================================================================

    /**
     * Apply a generic process action to a document.
     *
     * Supported actions: lifecycle.release, lifecycle.close, lifecycle.cancel,
     * approval.submit, approval.approve, posting.post, posting.reverse,
     * execution.start, execution.complete, payment.allocate
     *
     * @param action  compound action string (e.g. "lifecycle.release")
     * @param payload optional request body (null for most actions)
     */
    public ToolResponse documentApplyProcessAction(String entity,
                                                   long id,
                                                   String action,
                                                   Map<String, Object> payload,
                                                   String tenant,
                                                   String bearerToken) {

        final String toolName = "document.apply_process_action";

        // Resolve URL early — catches unknown action families before hitting backend
        final String backendPath;
        try {
            backendPath = urlResolver.resolveProcessAction(entity, id, action)
                    .replace(urlResolver.getApiBase(), "");
        } catch (IllegalArgumentException ex) {
            return ToolResponse.toolError(toolName, "tool_mapping_error", ex.getMessage(), tenant, "unknown");
        }

        if (entity == null || entity.isBlank()) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'entity' is required.", tenant, backendPath);
        }
        if (id <= 0) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'id' not valid.", tenant, backendPath);
        }
        if (action == null || action.isBlank()) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'action' is required (e.g. 'lifecycle.release').", tenant, backendPath);
        }

        try {
            Map<String, Object> raw = client.applyProcessAction(
                    entity, id, action, payload, bearerToken);

            if (isBusinessError(raw)) {
                return ToolResponse.error(toolName,
                        errorMapper.mapBusinessError(raw), tenant, backendPath);
            }

            Map<String, Object> data = Map.of(
                    "entity", entity,
                    "id",     id,
                    "action", action,
                    "state", raw.get("state") instanceof String s ? s : ""
            );

            log.debug("[{}] entity={} id={} action={} ok", toolName, entity, id, action);
            return ToolResponse.success(toolName, data, tenant, backendPath);

        } catch (AtracioBackendException ex) {
            NormalisedError err = errorMapper.map(ex.getHttpStatus(), ex.getBody());
            log.warn("[{}] backend error {} {}", toolName, ex.getHttpStatus(), err.code());
            return ToolResponse.error(toolName, err, tenant, backendPath);
        } catch (Exception ex) {
            NormalisedError err = errorMapper.mapException(ex);
            log.error("[{}] unexpected error", toolName, ex);
            return ToolResponse.error(toolName, err, tenant, backendPath);
        }
    }

    // =========================================================================
    // 5. wms.get_article_stock_summary
    // Aggregates multiple warehouse API calls into one consolidated view
    // =========================================================================

    /**
     * Provide a consolidated stock view for one article by aggregating:
     *   - quantity, forecast, valuation, entries, issues, turnover
     *
     * Each sub-call is attempted independently. Partial failures are logged
     * but do not abort the aggregation — the field is set to null if a
     * sub-call fails.
     */
    public ToolResponse wmsGetArticleStockSummary(long articleId,
                                                  Long siteId,
                                                  String tenant,
                                                  String bearerToken) {

        final String toolName    = "wms.get_article_stock_summary";

        Map<String, Object> meta = Map.of(
                    "tenant",      tenant != null ? tenant : "",
                    "aggregated",  true
        );

        if (articleId <= 0) {
            return ToolResponse.toolErrorWithMeta(toolName, "tool_mapping_error",
                    "Parameter 'articleId' not valid", meta);
        }

        try {
            Double  quantity        = safeDouble(() -> client.getArticleQuantity(articleId, siteId, bearerToken), toolName, "");
            Double  forecastQty     = safeDouble(() -> client.getArticleForecast(articleId, siteId, bearerToken), toolName, "");
            Double  valuation       = safeDouble(() -> client.getArticleValuation(articleId, bearerToken), toolName, "");
            Double  entriesThisYear = safeDouble(() -> client.getArticleEntries(articleId, bearerToken), toolName, "");
            Double  issuesThisYear  = safeDouble(() -> client.getArticleIssues(articleId, bearerToken), toolName, "");
            Double  turnover        = safeDouble(() -> client.getArticleTurnover(articleId, bearerToken), toolName, "");

            Map<String, Object> data = new HashMap<>();
            data.put("articleId",          articleId);
            data.put("siteId",             siteId);
            data.put("stockQuantity",      quantity);
            data.put("forecastQuantity",   forecastQty);
            data.put("valuation",          valuation);
            data.put("entriesCurrentYear", entriesThisYear);
            data.put("issuesCurrentYear",  issuesThisYear);
            data.put("turnover",           turnover);

            log.debug("[{}] articleId={} siteId={} ok", toolName, articleId, siteId);
           
            return ToolResponse.successWithMeta(toolName, data, meta);

        } catch (AtracioTokenExpiredException e){
            NormalisedError normalisedError = errorMapper.map(e.getHttpStatus(), e.getBody());
            return ToolResponse.errorWithMeta(toolName, normalisedError, Map.of("tenant", "demo", "aggregated", true));
        } catch (Exception ex) {
            NormalisedError err = errorMapper.mapException(ex);
            log.error("[{}] unexpected error", toolName, ex);
            return ToolResponse.errorWithMeta(toolName, err, meta);
        }
    }

    // =========================================================================
    // 6. wms.lookup_inventory_unit
    // GET /api/warehouse/units/{lookupType}?{lookupType}={value}
    // =========================================================================

    /**
     * Find an inventory unit by barcode, RFID tag, or serial number.
     *
     * @param lookupType "barcode" | "rfidTag" | "serialNumber"
     * @param value      the identifier value to search for
     */
    public ToolResponse wmsLookupInventoryUnit(String lookupType,
                                               String value,
                                               String tenant,
                                               String bearerToken) {

        final String toolName    = "wms.lookup_inventory_unit";
        final String backendPath = "/warehouse/units/" + lookupType;

        if (lookupType == null || lookupType.isBlank()) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'lookupType' is required: 'barcode', 'rfidTag', or 'serialNumber'.", tenant, backendPath);
        }
        if (!List.of("barcode", "rfidTag", "serialNumber").contains(lookupType)) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Unknown lookupType '" + lookupType + "'. Must be 'barcode', 'rfidTag', or 'serialNumber'.", tenant, backendPath);
        }
        if (value == null || value.isBlank()) {
            return ToolResponse.toolError(toolName, "tool_mapping_error",
                    "Parameter 'value' is required.", tenant, backendPath);
        }

        try {
            Map<String, Object> raw = client.lookupInventoryUnit(lookupType, value, bearerToken);

            if (isBusinessError(raw)) {
                return ToolResponse.error(toolName,
                        errorMapper.mapBusinessError(raw), tenant, backendPath);
            }

            Map<String, Object> data = Map.of(
                    "lookupType", lookupType,
                    "value",      value,
                    "unit",       raw != null ? raw : Map.of()
            );

            log.debug("[{}] lookupType={} value={} ok", toolName, lookupType, value);
            return ToolResponse.success(toolName, data, tenant, backendPath);

        } catch (AtracioBackendException ex) {
            NormalisedError err = errorMapper.map(ex.getHttpStatus(), ex.getBody());
            log.warn("[{}] backend error {} {}", toolName, ex.getHttpStatus(), err.code());
            return ToolResponse.error(toolName, err, tenant, backendPath);
        } catch (Exception ex) {
            NormalisedError err = errorMapper.mapException(ex);
            log.error("[{}] unexpected error", toolName, ex);
            return ToolResponse.error(toolName, err, tenant, backendPath);
        }
    }

    // =========================================================================
    // 7. partner.get_summary
    // Aggregates client or vendor commercial data
    // =========================================================================

    /**
     * Return a commercial summary for a client or vendor.
     *
     * @param partnerType "client" | "vendor"
     * @param partnerId   the client or vendor primary key
     */
    public ToolResponse partnerGetSummary(String partnerType,
                                          long partnerId,
                                          String tenant,
                                          String bearerToken) {

        final String toolName    = "partner.get_summary";

        Map<String, Object> meta = Map.of(
                    "tenant",      tenant != null ? tenant : "",
                    "aggregated",  true
        );

        if (partnerType == null || partnerType.isBlank()) {
            return ToolResponse.toolErrorWithMeta(toolName, "tool_mapping_error",
                    "Parameter 'partnerType' is required: 'client' or 'vendor'.", meta);
        }
        if (!List.of("client", "vendor").contains(partnerType)) {
            return ToolResponse.toolErrorWithMeta(toolName, "tool_mapping_error",
                    "Unknown partnerType '" + partnerType + "'. Must be 'client' or 'vendor'.", meta);
        }
        if (partnerId <= 0) {
            return ToolResponse.toolErrorWithMeta(toolName, "tool_mapping_error",
                    "Parameter 'partnerId' not valid.", meta);
        }

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("partnerType", partnerType);
            data.put("partnerId",   partnerId);

            if ("client".equals(partnerType)) {
                data.put("turnover",          safeDouble(() -> client.getClientTurnover(partnerId, bearerToken), toolName, ""));
                data.put("unpaidAmount",      safeDouble(() -> client.getClientUnpaidAmount(partnerId, bearerToken), toolName, ""));
                data.put("unpaidInvoices",    safeCall(()   -> client.getClientUnpaidInvoices(partnerId, bearerToken), toolName, ""));
                data.put("lastInvoiceDate",   safeCall(()   -> client.getClientLastInvoiceDate(partnerId, bearerToken), toolName, ""));
                data.put("salesOrdersCount",  safeCall(()   -> client.getClientSalesOrdersCount(partnerId, bearerToken), toolName, ""));
                data.put("lastSalesOrder",    safeCall(()   -> client.getClientLastSalesOrder(partnerId, bearerToken), toolName, ""));
            } else {
                data.put("turnover",               safeDouble(() -> client.getVendorTurnover(partnerId, bearerToken), toolName, ""));
                data.put("unpaidAmount",           safeDouble(() -> client.getVendorUnpaidAmount(partnerId, bearerToken), toolName, ""));
                data.put("unpaidInvoices",         safeCall(()   -> client.getVendorUnpaidInvoices(partnerId, bearerToken), toolName, ""));
                data.put("lastPurchaseOrderDate",  safeCall(()   -> client.getVendorLastPurchaseOrderDate(partnerId, bearerToken), toolName, ""));
            }

            log.debug("[{}] partnerType={} partnerId={} ok", toolName, partnerType, partnerId);
            
            return ToolResponse.successWithMeta(toolName, data, meta);

        } catch (Exception ex) {
            NormalisedError err = errorMapper.mapException(ex);
            log.error("[{}] unexpected error", toolName, ex);
            return ToolResponse.errorWithMeta(toolName, err,meta);
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Returns true when Atracio responded 2xx but with a business error body:
     *   { "status": "error", "response": "..." }
     */
    private boolean isBusinessError(Map<String, Object> raw) {
        return "error".equals(raw.get("status"));
    }

    private boolean isNotFoundError(Map<String, Object> raw) {
        return raw == null;
    }

    /**
     * Calls a supplier that returns a double. Returns null on any failure
     * instead of aborting the whole aggregation (used by stock summary and partner summary).
     */
    private Double safeDouble(DoubleSupplierThrows supplier, String toolName, String backendPath) {
        try {
            return supplier.get();
        }catch(AtracioBackendException e) {
            if (e.getBody().get("code") instanceof String code && "access_token_expired".equals(code)) {
                throw new AtracioTokenExpiredException(e.getHttpStatus(), e.getBody());
            } else {
                return null;
            }
        } catch (Exception ex) {
            log.warn("safeDouble: sub-call failed — {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Calls a supplier that returns any Object. Returns null on any failure.
     */
    private Object safeCall(ObjectSupplierThrows supplier, String toolName, String backendPath) {
        try {
            return supplier.get();
        }catch(AtracioBackendException e) {
            if (e.getBody().get("code") instanceof String code && "access_token_expired".equals(code)) {
                throw new AtracioTokenExpiredException(e.getHttpStatus(), e.getBody());
            } else {
                return null;
            }
        } catch (Exception ex) {
            log.warn("safeCall: safe-call failed — {}", ex.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    private interface DoubleSupplierThrows {
        double get() throws Exception;
    }

    @FunctionalInterface
    private interface ObjectSupplierThrows {
        Object get() throws Exception;
    }
}