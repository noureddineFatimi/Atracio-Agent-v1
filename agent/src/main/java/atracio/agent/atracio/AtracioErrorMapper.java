package atracio.agent.atracio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
 
/**
 * Translates raw Atracio backend responses and HTTP status codes into the
 * normalised error codes defined in the guide.
 *
 * The LLM and every tool see only normalised codes — never raw Atracio internals.
 *
 * Normalised error codes (from guide section 14.3):
 *   unauthorized       — token missing, invalid, or expired
 *   forbidden          — valid token but insufficient permissions
 *   tenant_mismatch    — token does not match the target tenant
 *   validation_error   — backend rejected the payload (business rule violation)
 *   not_found          — document or entity does not exist
 *   backend_error      — unexpected backend failure (5xx or unmapped error)
 *   tool_mapping_error — agent could not construct a valid request
 *   timeout            — backend did not respond in time
 *   unknown_error      — catch-all for anything else
 *
 * Atracio backend auth error codes (from guide section 8.4):
 *   access_token_expired / access_token_invalid / access_token_missing
 *   tenant_mismatch
 *   refresh_token_invalid / refresh_token_expired / refresh_token_reused
 *   session_not_found / session_revoked
 *   invalid_credentials / account_locked / account_expired / user_disabled
 */
@Component
public class AtracioErrorMapper {
 
    private static final Logger log = LoggerFactory.getLogger(AtracioErrorMapper.class);
 
    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------
 
    /**
     * Maps an HTTP status code + raw Atracio response body to a NormalisedError.
     *
     * Call this whenever the backend returns a non-2xx status
     *
     * @param httpStatus   the HTTP status code returned by Atracio (e.g. 401, 500)
     * @param responseBody the parsed JSON body from Atracio (may be null)
     * @return a NormalisedError ready to be placed in the tool response envelope
     */
    public NormalisedError map(int httpStatus, Map<String, Object> responseBody) {
        String backendCode    = extractBackendCode(responseBody);
        String backendMessage = extractBackendMessagefromTechnicalError(responseBody);
 
        String normalisedCode = normalise(httpStatus, backendCode);
 
        log.debug("AtracioErrorMapper: httpStatus={} backendCode={} -> normalisedCode={}",
                httpStatus, backendCode, normalisedCode);
 
        return new NormalisedError(
                normalisedCode,
                buildMessage(normalisedCode, backendMessage),
                httpStatus,
                backendCode != null ? backendCode : "backend_error"
        );
    }
 
    /**
     * Maps a response body that carries { "status": "error", "response": [{"...": "..."}] }
     * when the HTTP status was 2xx but the business operation failed.
     *
     * @param responseBody the parsed JSON body from Atracio
     * @return a NormalisedError, defaulting to validation_error for business failures
     */
    public NormalisedError mapBusinessError(Map<String, Object> responseBody) {
        String backendMessage = extractBackendMessagefromLogicError(responseBody);
        log.debug("AtracioErrorMapper: business error — message={}", backendMessage);
 
        return new NormalisedError(
                "validation_error",
                backendMessage != null ? backendMessage : "The operation was rejected by Atracio.",
                200,
                "business_error"
        );
    }
 
    /**
     * Maps a local exception (timeout, connection refused, JSON parse failure)
     * that occurred before any Atracio response was received.
     *
     * @param cause the exception
     * @return a NormalisedError with code timeout or backend_error
     */
    public NormalisedError mapException(Throwable cause) {
        log.warn("AtracioErrorMapper: local exception — {}", cause.getMessage());
 
        boolean isTimeout = cause.getClass().getSimpleName().toLowerCase().contains("timeout");
        String code = isTimeout ? "timeout" : "backend_error";
 
        return new NormalisedError(
                code,
                isTimeout
                        ? "The Atracio backend did not respond in time. Please try again."
                        : "An unexpected error occurred while contacting Atracio.",
                -1,
                cause.getClass().getSimpleName()
        );
    }
 
    // -------------------------------------------------------------------------
    // Core mapping logic
    // -------------------------------------------------------------------------
 
    private String normalise(int httpStatus, String backendCode) {
        // 1. Auth-level codes take priority regardless of HTTP status
        if (backendCode != null) {
            switch (backendCode) {
                case "access_token_expired":
                case "access_token_invalid":
                case "access_token_missing":
                case "refresh_token_invalid":
                case "refresh_token_expired":
                case "refresh_token_reused":
                case "session_not_found":
                case "session_revoked":
                case "invalid_credentials":
                case "account_locked":
                case "account_expired":
                case "user_disabled":
                    return "unauthorized";
 
                case "tenant_mismatch":
                    return "tenant_mismatch";
            }
        }
 
        // 2. Fall back to HTTP status
        return switch (httpStatus) {
            case 400 -> "validation_error";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 408, 504 -> "timeout";
            default  -> httpStatus >= 500 ? "backend_error" : "unknown_error";
        };
    }
 
    private String buildMessage(String normalisedCode, String backendMessage) {
        return switch (normalisedCode) {
            case "unauthorized"     ->
                    "Session expired or the access token is invalid. User should log in again.";
            case "forbidden"        ->
                    "Need permission to perform this action.";
            case "tenant_mismatch"  ->
                    "Access token does not match the target tenant.";
            case "validation_error" ->
                    backendMessage != null ? backendMessage : "The request rejected by Atracio due to a validation error.";
            case "not_found"        ->
                    backendMessage != null ? backendMessage : "The requested document or entity not found.";
            case "timeout"          ->
                    "The Atracio backend did not respond in time. User should try again.";
            case "backend_error"    ->
                    "An unexpected error occurred on the Atracio backend. User should try again later.";
            default                 ->
                    backendMessage != null ? backendMessage : "An unknown error occurred.";
        };
    }
 
    // -------------------------------------------------------------------------
    // Response body helpers
    // -------------------------------------------------------------------------
 
    /**
     * Extracts the Atracio error code from the response body.
     *
     * Example:
     *   { "code": "access_token_expired", "message": "..." }   — auth errors
     */
    private String extractBackendCode(Map<String, Object> body) {
        if (body == null) return null;
        Object code = body.get("code");
        return code instanceof String s ? s : null;
    }
 
    private String extractBackendMessagefromLogicError(Map<String, Object> body) {
        if (body == null) return null;
 
        // Business error shape: { "status": "error", "response": [{"...": "..."}] }
        Object response = body.get("response");
        if (response instanceof List<?>) {
                List<?> list = (List<?>) response;

                String message = list.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .map(m -> m.get("completeErrorMessage"))
                        .filter(val -> val instanceof String)    
                        .map(String.class::cast)                
                        .collect(Collectors.joining(", "));

                return message.isEmpty() ? null : message;
        }

        return null;
    }

    private String extractBackendMessagefromTechnicalError(Map<String, Object> body) {
        if (body == null) return null;
 
        // Auth error shape: { "message": "..." }
        Object message = body.get("message");
        if (message instanceof String s && !s.isBlank()) return s;
 
        return null;
    }
 
    // -------------------------------------------------------------------------
    // NormalisedError — immutable value object
    // -------------------------------------------------------------------------
 
    /**
     * Represents a normalised error ready to be embedded in a ToolResponse error envelope.
     *
     * Maps directly to the guide's error shape (section 14.2):
     * {
     *   "code":          "unauthorized",
     *   "message":       "Your session has expired...",
     *   "backendStatus": 401,
     *   "backendCode":   "access_token_expired"
     * }
     */
    public record NormalisedError(
            String code,
            String message,
            int    backendStatus,
            String backendCode
    ) {
        public boolean isUnauthorized()   { return "unauthorized".equals(code); }
        public boolean isNotFound()       { return "not_found".equals(code); }
        public boolean isValidation()     { return "validation_error".equals(code); }
        public boolean isTenantMismatch() { return "tenant_mismatch".equals(code); }
    }
}