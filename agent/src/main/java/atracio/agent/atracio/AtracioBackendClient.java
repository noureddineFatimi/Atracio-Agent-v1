package atracio.agent.atracio;

import java.util.List;
import java.util.Map;
 
/**
 * Contract for all outbound calls to the Atracio backend.
 *
 * The agent service must never call Atracio directly from tools or orchestrator.
 * All HTTP interactions go through this interface so that:
 *   - the mock implementation can replace the real one without changing any caller
 *   - tests never depend on a live Atracio environment
 *   - the bearer token is always passed explicitly — never stored or shared
 *
 * Every method receives:
 *   - bearerToken : the current user's access token, forwarded as-is to Atracio
 *
 * Every method returns a raw Map<String, Object> representing the Atracio JSON
 * response body. AtracioErrorMapper is responsible for translating backend errors
 * into normalised agent error codes before the result reaches any tool.
 *
 * Phase 2: AtracioBackendClientMock implements this interface.
 * Phase 5: AtracioBackendClientHttp implements this interface with real RestClient calls.
 */
public interface AtracioBackendClient {
 
    // -------------------------------------------------------------------------
    // Generic entity operations
    // -------------------------------------------------------------------------
 
    /**
     * POST /api/entities/list/{entity}
     *
     * @param entity      Atracio entity key (e.g. "SalesOrder", "PurchaseOrder")
     * @param requestBody the list request payload (filter, page, size, sort, fieldsToFetch…)
     * @param bearerToken user access token
     * @return raw Atracio response body
     */
    Map<String, Object> listEntities(String entity,
                                     Map<String, Object> requestBody,
                                     String bearerToken);
 
    /**
     * GET /api/entities/details/{entity}/{id}
     *
     * @param entity      Atracio entity key
     * @param id          document primary key
     * @param bearerToken user access token
     * @return raw Atracio response body
     */
    Map<String, Object> getEntityDetails(String entity,
                                         long id,
                                         String bearerToken);
 
    /**
     * POST /api/entities/save/{entity}
     *
     * @param entity      Atracio entity key
     * @param requestBody { "entity": {...}, "customFieldValues": [] }
     * @param bearerToken user access token
     * @return raw Atracio response body
     */
    Map<String, Object> saveEntity(String entity,
                                   Map<String, Object> requestBody,
                                   String bearerToken);

    /**
     * POST /api/entities/validate/{entity}
     *
     * @param entity      Atracio entity key
     * @param requestBody { "entity": {...}, "customFieldValues": [] }
     * @param bearerToken user access token
     * @return raw Atracio response body
     */
    List<Map<String, Object>> validateEntity(String entity,
                                   Map<String, Object> requestBody,
                                   String bearerToken);

    /**
     * DELETE  /api/entities/delete/{entity}/{ids}
     *
     * @param entity      Atracio entity key
     * @param requestBody { "entity": {...}, "customFieldValues": [] }
     * @param bearerToken user access token
     * @return raw Atracio response body
     */
    Map<String, Object> deleteEntity(String entity, 
                                    String ids,
                                    String bearerToken);

    // -------------------------------------------------------------------------
    // Generic process actions
    // -------------------------------------------------------------------------
 
    /**
     * POST /api/process/{family}/{entity}/{id}/{verb}
     *
     * The full URL is resolved by AtracioUrlResolver.resolveProcessAction.
     *
     * @param entity      Atracio entity key
     * @param id          document primary key
     * @param action      compound action string from the tool contract (e.g. "lifecycle.release")
     * @param payload     optional request body (null for most lifecycle/approval actions)
     * @param bearerToken user access token
     * @return raw Atracio response body
     */
    Map<String, Object> applyProcessAction(String entity,
                                           long id,
                                           String action,
                                           Map<String, Object> payload,
                                           String bearerToken);
 
    // -------------------------------------------------------------------------
    // WMS — article stock
    // -------------------------------------------------------------------------
 
    /**
     * GET /api/warehouse/article/quantity/{articleId}?siteId={siteId}
     */
    Double getArticleQuantity(long articleId,
                                           Long siteId,
                                           String bearerToken);
 
    /**
     * GET /api/warehouse/article/quantity/{articleId}/forecast?siteId={siteId}
     */
    Double getArticleForecast(long articleId,
                                           Long siteId,
                                           String bearerToken);
 
    /**
     * GET /api/warehouse/article/valuation/{articleId}
     */
    Double getArticleValuation(long articleId,
                                            String bearerToken);
 
    /**
     * GET /api/warehouse/article/entries/{articleId}
     */
    Double getArticleEntries(long articleId,
                                          String bearerToken);
 
    /**
     * GET /api/warehouse/article/issues/{articleId}
     */
    Double getArticleIssues(long articleId,
                                         String bearerToken);
 
    /**
     * GET /api/warehouse/article/turnover/{articleId}
     */
    Double getArticleTurnover(long articleId,
                                           String bearerToken);

    /**
     * GET /api/warehouse/article/stock-evolution/{articleId}
     */
    List<Map<String, Object>> getStockEvolution(long articleId,
                                           String bearerToken);
 
    // -------------------------------------------------------------------------
    // WMS — inventory unit lookup
    // -------------------------------------------------------------------------
 
    /**
     * GET /api/warehouse/units/barcode?barcode={value}
     *      /api/warehouse/units/rfidTag?rfidTag={value}
     *      /api/warehouse/units/serialNumber?serialNumber={value}
     *
     * @param lookupType "barcode" | "rfidTag" | "serialNumber"
     * @param value      the identifier to look up
     * @param bearerToken user access token
     */
    Map<String, Object> lookupInventoryUnit(String lookupType,
                                            String value,
                                            String bearerToken);
 
    // -------------------------------------------------------------------------
    // Partner — client
    // -------------------------------------------------------------------------
 
    Double getClientTurnover(long clientId, String bearerToken);
 
    Double getClientUnpaidAmount(long clientId, String bearerToken);
 
    List<Map<String, Object>> getClientUnpaidInvoices(long clientId, String bearerToken);
 
    Long getClientLastInvoiceDate(long clientId, String bearerToken);
 
    Integer getClientSalesOrdersCount(long clientId, String bearerToken);
 
    Map<String, Object> getClientLastSalesOrder(long clientId, String bearerToken);
 
    // -------------------------------------------------------------------------
    // Partner — vendor
    // -------------------------------------------------------------------------
 
    Double getVendorTurnover(long vendorId, String bearerToken);
 
    Double getVendorUnpaidAmount(long vendorId, String bearerToken);
 
    List<Map<String, Object>> getVendorUnpaidInvoices(long vendorId, String bearerToken);
 
    Long getVendorLastPurchaseOrderDate(long vendorId, String bearerToken);
}