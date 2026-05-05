package atracio.agent.tools;

import atracio.agent.atracio.AtracioErrorMapper.NormalisedError;

import java.util.Map;

/**
 * Normalised response envelope returned by every tool.
 *
 * The LLM and the ChatController never see raw Atracio responses —
 * they always receive this shape (guide section 14).
 *
 * Success shape:
 * {
 *   "ok":    true,
 *   "tool":  "document.search",
 *   "data":  { ... },
 *   "error": null,
 *   "meta":  { "tenant": "demo", "backendPath": "/entities/list/SalesOrder" }
 * }
 *
 * Error shape:
 * {
 *   "ok":    false,
 *   "tool":  "document.search",
 *   "data":  null,
 *   "error": {
 *     "code":          "unauthorized",
 *     "message":       "Your session has expired...",
 *     "backendStatus": 401,
 *     "backendCode":   "access_token_expired"
 *   },
 *   "meta":  { "tenant": "demo", "backendPath": "/entities/list/SalesOrder" }
 * }
 *
 * Usage:
 *   return ToolResponse.success("document.search", data, tenant, backendPath);
 *   return ToolResponse.error("document.search", normalisedError, tenant, backendPath);
 */
public class ToolResponse {

    private final boolean         ok;
    private final String          tool;
    private final Object          data;
    private final ErrorPayload    error;
    private final Map<String, Object> meta;

    // -------------------------------------------------------------------------
    // Private constructor — use factory methods
    // -------------------------------------------------------------------------

    private ToolResponse(boolean ok,
                         String tool,
                         Object data,
                         ErrorPayload error,
                         Map<String, Object> meta) {
        this.ok    = ok;
        this.tool  = tool;
        this.data  = data;
        this.error = error;
        this.meta  = meta;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Builds a success response.
     *
     * @param tool        tool name (e.g. "document.search")
     * @param data        the normalised payload to send to the LLM
     * @param tenant      tenant identifier (e.g. "demo")
     * @param backendPath the Atracio API path that was called (e.g. "/entities/list/SalesOrder")
     */
    public static ToolResponse success(String tool,
                                       Object data,
                                       String tenant,
                                       String backendPath) {
        return new ToolResponse(true, tool, data, null, meta(tenant, backendPath));
    }

    /**
     * Builds an error response from a NormalisedError.
     *
     * @param tool           tool name
     * @param normalisedError the error produced by AtracioErrorMapper
     * @param tenant         tenant identifier
     * @param backendPath    the Atracio API path that was attempted
     */
    public static ToolResponse error(String tool,
                                     NormalisedError normalisedError,
                                     String tenant,
                                     String backendPath) {
        ErrorPayload payload = new ErrorPayload(
                normalisedError.code(),
                normalisedError.message(),
                normalisedError.backendStatus(),
                normalisedError.backendCode()
        );
        return new ToolResponse(false, tool, null, payload, meta(tenant, backendPath));
    }

     /**
     * Builds an error response from a NormalisedError.
     *
     * @param tool           tool name
     * @param normalisedError the error produced by AtracioErrorMapper
     * @param tenant         tenant identifier
     */
    public static ToolResponse errorWithMeta(String tool,
                                     NormalisedError normalisedError,
                                     Map<String, Object> customMeta
                                     ) {
        ErrorPayload payload = new ErrorPayload(
                normalisedError.code(),
                normalisedError.message(),
                normalisedError.backendStatus(),
                normalisedError.backendCode()
        );
        return new ToolResponse(false, tool, null, payload, customMeta);
    }

     /**
     * Builds a success response with a custom meta map.
     * Use this for aggregated tools (e.g. wms.get_article_stock_summary) that
     * need extra meta fields like "aggregated: true".
     */
    public static ToolResponse successWithMeta(String tool,
                                               Object data,
                                               Map<String, Object> customMeta) {
        return new ToolResponse(true, tool, data, null, customMeta);
    }

    /**
     * Builds an error response for tool-level failures (bad input, mapping error)
     * that never reached the backend.
     *
     * @param tool    tool name
     * @param code    normalised error code (e.g. "tool_mapping_error")
     * @param message human-readable explanation
     */
    public static ToolResponse toolError(String tool, String code, String message, String tenant, String backendPath) {
        ErrorPayload payload = new ErrorPayload(code, message, -1, null);
        return new ToolResponse(false, tool, null, payload, meta(tenant, backendPath));
    }

    public static ToolResponse toolErrorWithMeta(String tool, String code, String message, Map<String, Object> customMeta) {
        ErrorPayload payload = new ErrorPayload(code, message, -1, null);
        return new ToolResponse(false, tool, null, payload, customMeta);
    }

    public static ToolResponse agentError(String error) {
        return new ToolResponse(false, "", null, new ErrorPayload("error", error, -1, null), Map.of());
    }

    // -------------------------------------------------------------------------
    // Accessors — used by Jackson serialization and tests
    // -------------------------------------------------------------------------

    public boolean isOk()                    { return ok; }
    public String  getTool()                 { return tool; }
    public Object  getData()                 { return data; }
    public ErrorPayload getError()           { return error; }
    public Map<String, Object> getMeta()     { return meta; }

    // -------------------------------------------------------------------------
    // Meta builder
    // -------------------------------------------------------------------------

    private static Map<String, Object> meta(String tenant, String backendPath) {
        return Map.of(
                "tenant",      tenant      != null ? tenant      : "",
                "backendPath", backendPath != null ? backendPath : ""
        );
    }

    // -------------------------------------------------------------------------
    // ErrorPayload — maps exactly to guide section 14.2
    // -------------------------------------------------------------------------

    public record ErrorPayload(
            String code,
            String message,
            int    backendStatus,
            String backendCode
    ) {}

    // -------------------------------------------------------------------------
    // toString — for console runner output and logs
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        if (ok) {
            return "ToolResponse{ok=true, tool='" + tool + "', data=" + data + "}";
        }
        return "ToolResponse{ok=false, tool='" + tool + "', error=" + error + "}";
    }
}