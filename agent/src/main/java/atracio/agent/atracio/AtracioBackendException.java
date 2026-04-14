package atracio.agent.atracio;

import java.util.Map;
 
/**
 * Thrown by AtracioBackendClientHttp (Phase 5) when Atracio returns a non-2xx
 * HTTP response.
 *
 * Carries the raw HTTP status and parsed body so that AtracioErrorMapper can
 * produce the correct normalised error code without losing any context.
 *
 * The mock implementation never throws this — it always returns a success Map.
 * Only the real HTTP client raises it.
 *
 * Usage in ToolExecutor (Phase 3):
 * Example of ToolExecutor implementation
 *   try {
 *       Map<String, Object> raw = client.listEntities(entity, body, token);
 *
 *       // 2xx received — but Atracio may still signal a business error
 *       if ("error".equals(raw.get("status"))) {
 *           NormalisedError err = errorMapper.mapBusinessError(raw);
 *           return ToolResponse.error(toolName, err, backendPath);
 *       }
 *
 *       // Genuine success — extract the "response" payload
 *       Object data = raw.get("response");
 *       return ToolResponse.success(toolName, data, backendPath);
 *
 *   } catch (AtracioBackendException ex) {
 *       NormalisedError err = errorMapper.map(ex.getHttpStatus(), ex.getBody());
 *       return ToolResponse.error(toolName, err, backendPath);
 *
 *   } catch (Exception ex) {
 *       NormalisedError err = errorMapper.mapException(ex);
 *       return ToolResponse.error(toolName, err, backendPath);
 *   }
 */
public class AtracioBackendException extends RuntimeException {
 
    private final int                 httpStatus;
    private final Map<String, Object> body;
 
    public AtracioBackendException(int httpStatus, Map<String, Object> body) {
        super("Atracio returned HTTP " + httpStatus);
        this.httpStatus = httpStatus;
        this.body       = body;
    }
 
    public AtracioBackendException(int httpStatus, Map<String, Object> body, Throwable cause) {
        super("Atracio returned HTTP " + httpStatus, cause);
        this.httpStatus = httpStatus;
        this.body       = body;
    }
 
    /** The HTTP status code returned by Atracio (e.g. 401, 404, 500). */
    public int getHttpStatus() {
        return httpStatus;
    }
 
    /**
     * The parsed JSON body from Atracio. May be null if the response had no body
     * or if JSON parsing itself failed.
     */
    public Map<String, Object> getBody() {
        return body;
    }
}