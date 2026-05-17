package atracio.agent.atracio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Real HTTP implementation of AtracioBackendClient using Spring RestClient.
 *
 * Active under profile "http".
 * Replace profile "mock" with "http" in application.yml to use this.
 *
 * Behaviour:
 *   - Every request forwards the bearer token as Authorization: Bearer {token}
 *   - Non-2xx responses → AtracioBackendException(httpStatus, parsedBody)
 *   - Network/timeout errors → the exception propagates as-is for
 *     AtracioErrorMapper.mapException() to handle in ToolExecutor
 *   - 2xx responses with { "status": "error" } are returned as-is (Map)
 *     so ToolExecutor can call errorMapper.mapBusinessError()
 *
 * Methods returning primitives (double, Long, Integer) extract the scalar
 * from the Atracio response envelope: { "status": "success", "response": 120.0 }
 */
@Component
@Profile("http")
public class AtracioBackendClientHttp implements AtracioBackendClient {

    private static final Logger log = LoggerFactory.getLogger(AtracioBackendClientHttp.class);

    private final RestClient   restClient;
    private final AtracioUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public AtracioBackendClientHttp(RestClient atracioRestClient,
                                     AtracioUrlResolver urlResolver,
                                     ObjectMapper objectMapper) {
        this.restClient   = atracioRestClient;
        this.urlResolver  = urlResolver;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Generic entity operations
    // =========================================================================

    @Override
    public Map<String, Object> listEntities(String entity,
                                            Map<String, Object> requestBody,
                                            String bearerToken) {
        String path = "/entities/list/" + entity;
        log.debug("[HTTP] POST {}", path);
        return post(path, requestBody, bearerToken, new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @Override
    public Map<String, Object> getEntityDetails(String entity,
                                                long id,
                                                String bearerToken) {
        String path = "/entities/details/" + entity + "/" + id;
        log.debug("[HTTP] GET {}", path);
        return get(path, bearerToken, new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @Override
    public Map<String, Object> saveEntity(String entity,
                                          Map<String, Object> requestBody,
                                          String bearerToken) {
        String path = "/entities/save/" + entity;
        log.debug("[HTTP] POST {}", path);
        return post(path, requestBody, bearerToken, new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @Override
    public  List<Map<String, Object>> validateEntity(String entity,
                                              Map<String, Object> requestBody,
                                              String bearerToken) {
        String path = "/entities/validate/" + entity;
        log.debug("[HTTP] POST {}", path);
        return post(path, requestBody, bearerToken, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @Override
    public Map<String, Object> deleteEntity(String entity,
                                            String ids,
                                            String bearerToken) {
        String path = "/entities/delete/" + entity + "/" + ids;
        log.debug("[HTTP] DELETE {}", path);
        return delete(path, bearerToken);
    }

    // =========================================================================
    // Process actions
    // =========================================================================

    @Override
    public Map<String, Object> applyProcessAction(String entity,
                                                  long id,
                                                  String action,
                                                  Map<String, Object> payload,
                                                  String bearerToken) {
        // Full URL resolved by AtracioUrlResolver, then strip base to get relative path
        String fullUrl   = urlResolver.resolveProcessAction(entity, id, action);
        String path      = fullUrl.replace(urlResolver.getApiBase(), "");
        log.debug("[HTTP] POST {}", path);
        return post(path, payload != null ? payload : Map.of(), bearerToken, new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    // =========================================================================
    // WMS — article stock  (return primitives extracted from envelope)
    // =========================================================================

    @Override
    public Double getArticleQuantity(long articleId, Long siteId, String bearerToken) {
        String path = buildArticlePath("/warehouse/article/quantity/", articleId, siteId);
        return get(path, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public Double getArticleForecast(long articleId, Long siteId, String bearerToken) {
        
        // Use the dedicated forecast path from resolver
        String fullPath = "/warehouse/article/quantity/" + articleId + "/forecast"
                + (siteId != null ? "?siteId=" + siteId : "");
        return get(fullPath, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public Double getArticleValuation(long articleId, String bearerToken) {
        return get("/warehouse/article/valuation/" + articleId, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public Double getArticleEntries(long articleId, String bearerToken) {
        return get("/warehouse/article/entries/" + articleId, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public Double getArticleIssues(long articleId, String bearerToken) {
        return get("/warehouse/article/issues/" + articleId, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public Double getArticleTurnover(long articleId, String bearerToken) {
        return get("/warehouse/article/turnover/" + articleId, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public List<Map<String, Object>> getStockEvolution(long articleId, String bearerToken) {
        return get("/warehouse/article/stock-evolution/" + articleId, bearerToken, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    // =========================================================================
    // WMS — inventory unit lookup
    // =========================================================================

    @Override
    public Map<String, Object> lookupInventoryUnit(String lookupType,
                                                   String value,
                                                   String bearerToken) {
        String path = switch (lookupType) {
            case "barcode"      -> "/warehouse/units/barcode?barcode="      + value;
            case "rfidTag"      -> "/warehouse/units/rfidTag?rfidTag="      + value;
            case "serialNumber" -> "/warehouse/units/serialNumber?serialNumber=" + value;
            default -> throw new IllegalArgumentException("Unknown lookupType: " + lookupType);
        };
        log.debug("[HTTP] GET {}", path);
        return get(path, bearerToken, new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    // =========================================================================
    // Partner — client
    // =========================================================================

    @Override
    public Double getClientTurnover(long clientId, String bearerToken) {
        return get("/client/turnover/" + clientId, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public Double getClientUnpaidAmount(long clientId, String bearerToken) {
        return get("/client/unpaid-amount/" + clientId, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public List<Map<String, Object>> getClientUnpaidInvoices(long clientId, String bearerToken) {
        return get("/client/unpaid-invoices/" + clientId, bearerToken, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @Override
    public Long getClientLastInvoiceDate(long clientId, String bearerToken) {
        return get("/client/last-invoice-date/" + clientId, bearerToken, new ParameterizedTypeReference<Long>() {});
    }

    @Override
    public Integer getClientSalesOrdersCount(long clientId, String bearerToken) {
        return get("/client/sales-orders/" + clientId + "/count", bearerToken, new ParameterizedTypeReference<Integer>() {});
    }

    @Override
    public Map<String, Object> getClientLastSalesOrder(long clientId, String bearerToken) {
        return get("/client/sales-orders/" + clientId + "/last", bearerToken, new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    // =========================================================================
    // Partner — vendor
    // =========================================================================

    @Override
    public Double getVendorTurnover(long vendorId, String bearerToken) {
        return get("/vendor/details/turnover/" + vendorId, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public Double getVendorUnpaidAmount(long vendorId, String bearerToken) {
        return get("/vendor/details/unpaid-amount/" + vendorId, bearerToken, new ParameterizedTypeReference<Double>() {});
    }

    @Override
    public List<Map<String, Object>> getVendorUnpaidInvoices(long vendorId, String bearerToken) {
        return get("/vendor/details/unpaid-purchase-invoices/" + vendorId, bearerToken, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @Override
    public Long getVendorLastPurchaseOrderDate(long vendorId, String bearerToken) {
        return get("/vendor/details/last-purchase-order-date/" + vendorId, bearerToken, new ParameterizedTypeReference<Long>() {});
    }

    // =========================================================================
    // HTTP primitives — GET / POST / DELETE
    // =========================================================================

    /**
     * Executes a GET request and returns the parsed response body.
     * Throws AtracioBackendException on non-2xx.
     */
    private <T> T get(String path, String bearerToken, ParameterizedTypeReference<T> responseType) {
        return restClient.get()
                .uri(path)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    Map<String, Object> errorBody = parseErrorBody(res);
                    log.warn("[HTTP] GET {} → {}", path, res.getStatusCode().value());
                    throw new AtracioBackendException(res.getStatusCode().value(), errorBody);
                })
                .body(responseType);
    }

    /**
     * Executes a POST request with a JSON body and returns the parsed response body.
     * Throws AtracioBackendException on non-2xx.
     */
    private <T> T post(String path,
                        Object requestBody,
                        String bearerToken,
                        ParameterizedTypeReference<T> responseType) {
        return restClient.post()
                .uri(path)
                .header("Authorization", "Bearer " + bearerToken)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    Map<String, Object> errorBody = parseErrorBody(res);
                    log.warn("[HTTP] POST {} → {}", path, res.getStatusCode().value());
                    throw new AtracioBackendException(res.getStatusCode().value(), errorBody);
                })
                .body(responseType);
    }

    /**
     * Executes a DELETE request and returns the parsed response body.
     * Throws AtracioBackendException on non-2xx.
     */
    private Map<String, Object> delete(String path, String bearerToken) {
        return restClient.delete()
                .uri(path)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    Map<String, Object> errorBody = parseErrorBody(res);
                    log.warn("[HTTP] DELETE {} → {}", path, res.getStatusCode().value());
                    throw new AtracioBackendException(res.getStatusCode().value(), errorBody);
                })
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});
    }
    
    // =========================================================================
    // Internal helpers
    // =========================================================================

    private String buildArticlePath(String base, long articleId, Long siteId) {
        String path = base + articleId;
        return siteId != null ? path + "?siteId=" + siteId : path;
    }

    /**
     * Attempts to parse the error response body as JSON.
     * Falls back to an empty map if the body is missing or unparseable.
     */
    private Map<String, Object> parseErrorBody(
            org.springframework.http.client.ClientHttpResponse res) {
        try {
            byte[] bytes = res.getBody().readAllBytes();
            if (bytes.length == 0) return Map.of();
            return objectMapper.readValue(bytes, new TypeReference<>() {});
        } catch (Exception ex) {
            log.debug("Could not parse error body: {}", ex.getMessage());
            return Map.of();
        }
    }
}