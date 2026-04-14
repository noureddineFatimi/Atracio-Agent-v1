package atracio.agent.atracio;

import atracio.agent.atracio.AtracioErrorMapper.NormalisedError;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
 
import static org.assertj.core.api.Assertions.assertThat;
 
class AtracioErrorMapperTest {
 
    private final AtracioErrorMapper mapper = new AtracioErrorMapper();
 
    // -------------------------------------------------------------------------
    // Auth error codes → unauthorized (regardless of HTTP status)
    // -------------------------------------------------------------------------
 
    @Nested
    class AuthErrorCodes {
 
        @ParameterizedTest(name = "backendCode={0} -> unauthorized")
        @CsvSource({
            "access_token_expired",
            "access_token_invalid",
            "access_token_missing",
            "refresh_token_invalid",
            "refresh_token_expired",
            "refresh_token_reused",
            "session_not_found",
            "session_revoked",
            "invalid_credentials",
            "account_locked",
            "account_expired",
            "user_disabled",
        })
        void shouldMapAuthCodesToUnauthorized(String backendCode) {
            Map<String, Object> body = Map.of(
                    "code",    backendCode,
                    "message", "Auth error."
            );
 
            NormalisedError error = mapper.map(401, body);
 
            assertThat(error.code()).isEqualTo("unauthorized");
            assertThat(error.backendCode()).isEqualTo(backendCode);
            assertThat(error.backendStatus()).isEqualTo(401);
            assertThat(error.isUnauthorized()).isTrue();
        }
 
        @Test
        void shouldMapTenantMismatch() {
            Map<String, Object> body = Map.of(
                    "code",    "tenant_mismatch",
                    "message", "Token does not match tenant."
            );
 
            NormalisedError error = mapper.map(401, body);
 
            assertThat(error.code()).isEqualTo("tenant_mismatch");
            assertThat(error.isTenantMismatch()).isTrue();
        }
 
        @Test
        void authCodeTakesPriorityOverHttpStatus() {
            // Even if HTTP status is 500, an auth backend code wins
            Map<String, Object> body = Map.of(
                    "code",    "access_token_expired",
                    "message", "Expired."
            );
 
            NormalisedError error = mapper.map(500, body);
 
            assertThat(error.code()).isEqualTo("unauthorized");
        }
    }
 
    // -------------------------------------------------------------------------
    // HTTP status fallback (no backend code)
    // -------------------------------------------------------------------------
 
    @Nested
    class HttpStatusFallback {
 
        @ParameterizedTest(name = "HTTP {0} -> {1}")
        @CsvSource({
            "400, validation_error",
            "401, unauthorized",
            "403, forbidden",
            "404, not_found",
            "408, timeout",
            "500, backend_error",
            "502, backend_error",
            "503, backend_error",
            "504, timeout",
        })
        void shouldMapHttpStatusToNormalisedCode(int httpStatus, String expectedCode) {
            NormalisedError error = mapper.map(httpStatus, null);
            assertThat(error.code()).isEqualTo(expectedCode);
        }
 
        @Test
        void unknownStatusBelow500() {
            NormalisedError error = mapper.map(418, null); // I'm a teapot
            assertThat(error.code()).isEqualTo("unknown_error");
        }
 
        @Test
        void nullBodyDoesNotThrow() {
            NormalisedError error = mapper.map(404, null);
            assertThat(error.code()).isEqualTo("not_found");
            assertThat(error.backendCode()).isEqualTo("backend_error");
        }
    }
 
    // -------------------------------------------------------------------------
    // Business error (2xx with status=error)
    // -------------------------------------------------------------------------
 
    @Nested
    class BusinessError {
 
        @Test
        void shouldMapBusinessErrorToValidationError() {

            Map<String, String> test_response_1 = Map.of("completeErrorMessage", "Cannot release a document with missing mandatory fields.");
            Map<String, String> test_response_2 = Map.of("completeErrorMessage", "'Currency' must not be null");
            List<Map<String, String>> test_response_list = new ArrayList<>();
            test_response_list.add(test_response_1);
            test_response_list.add(test_response_2);
            Map<String, Object> body = Map.of(
                    "status",   "error",
                    "response", test_response_list
            );
 
            NormalisedError error = mapper.mapBusinessError(body);
 
            assertThat(error.code()).isEqualTo("validation_error");
            assertThat(error.message()).isEqualTo("Cannot release a document with missing mandatory fields., 'Currency' must not be null");
            assertThat(error.isValidation()).isTrue();
        }
 
        @Test
        void shouldMapBusinessErrorToValidationErrorWithAParssedJSON() {

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String json_body = """
                                            {
                    "status": "error",
                    "response": [
                        {
                            "entityFieldMetadata": {
                                "entityClass": "com.atracio.domain.model.sales.order.SalesOrder",
                                "name": "site",
                                "displayKey": "{entities.Site.singular}",
                                "displayName": "Site",
                                "mainProperty": "code",
                                "formDisplayProperty": null,
                                "type": "object",
                                "underlyingTypes": [
                                    "Site",
                                    "name"
                                ],
                                "flagFields": [],
                                "parentExplodedField": null,
                                "settings": {},
                                "required": true,
                                "unique": false,
                                "computed": false,
                                "customField": false,
                                "fieldOrder": 105,
                                "gridSpan": 0,
                                "newLineBefore": true,
                                "newLineAfter": false,
                                "newLineIf": "model.entityName !== 'MaintenanceWorkOrder'",
                                "cascadeOnPersist": false,
                                "dependency": null,
                                "dependencyField": null,
                                "formPage": "10- General",
                                "width": null,
                                "searchKey": true,
                                "recomputeOnServer": true,
                                "showIf": null,
                                "readOnlyIf": "model.readOnly || ((model.withSource && !model.withMaintenanceSource) || model.inventoryUnit)",
                                "readOnlyEntity": false,
                                "dynamicAddAllowed": false,
                                "filtersDependencies": "AND",
                                "filters": [
                                    {
                                        "filterKey": "company",
                                        "operator": "EQUAL",
                                        "filterValues": "model.company.id",
                                        "disableIf": ""
                                    },
                                    {
                                        "filterKey": "flagStockManagement",
                                        "operator": "IN",
                                        "filterValues": "model.siteFlagStockManagementFilter",
                                        "disableIf": ""
                                    },
                                    {
                                        "filterKey": "flagAssetManagement",
                                        "operator": "IN",
                                        "filterValues": "model.siteFlagAssetManagementFilter",
                                        "disableIf": ""
                                    }
                                ],
                                "min": null,
                                "max": null,
                                "clazz": "com.atracio.domain.model.location.Site",
                                "formHint": null,
                                "showAsColumn": false
                            },
                            "errorMessage": "must not be null",
                            "rowIdentifier": null,
                            "completeErrorMessage": "'Site' must not be null"
                        },
                        {
                            "entityFieldMetadata": {
                                "entityClass": "com.atracio.domain.model.sales.order.SalesOrder",
                                "name": "currency",
                                "displayKey": "{entities.Currency.singular}",
                                "displayName": "Currency",
                                "mainProperty": "code",
                                "formDisplayProperty": "code",
                                "type": "object",
                                "underlyingTypes": [
                                    "Currency",
                                    "code"
                                ],
                                "flagFields": [],
                                "parentExplodedField": null,
                                "settings": {},
                                "required": true,
                                "unique": false,
                                "computed": false,
                                "customField": false,
                                "fieldOrder": 140,
                                "gridSpan": 0,
                                "newLineBefore": false,
                                "newLineAfter": false,
                                "newLineIf": null,
                                "cascadeOnPersist": false,
                                "dependency": null,
                                "dependencyField": null,
                                "formPage": "10- General",
                                "width": null,
                                "searchKey": false,
                                "recomputeOnServer": false,
                                "showIf": null,
                                "readOnlyIf": null,
                                "readOnlyEntity": false,
                                "dynamicAddAllowed": true,
                                "filtersDependencies": null,
                                "filters": [],
                                "min": null,
                                "max": null,
                                "clazz": "com.atracio.domain.model.common.Currency",
                                "formHint": null,
                                "showAsColumn": false
                            },
                            "errorMessage": "must not be null",
                            "rowIdentifier": null,
                            "completeErrorMessage": "'Currency' must not be null"
                        }
                    ]
                }
                """;
            
            Map<String, Object> body = objectMapper.readValue(json_body, new TypeReference<Map<String, Object>>() {});
 
            NormalisedError error = mapper.mapBusinessError(body);
 
            assertThat(error.code()).isEqualTo("validation_error");
            assertThat(error.message()).isEqualTo("'Site' must not be null, 'Currency' must not be null");
            assertThat(error.isValidation()).isTrue();
            } catch (JsonProcessingException e) {
                System.out.println("Erreur:" + e);
            }
        }

        @Test
        void shouldFallbackMessageWhenResponseFieldIsMissing() {
            Map<String, Object> body = Map.of("status", "error");
 
            NormalisedError error = mapper.mapBusinessError(body);
 
            assertThat(error.code()).isEqualTo("validation_error");
            assertThat(error.message()).isEqualTo("The operation was rejected by Atracio.");
        }

        @Test
        void shouldFallbackMessageWhenResponseFieldIsEmtpty() {
            List<Object> empty_list = new ArrayList<Object>();
            Map<String, Object> body = Map.of("status", "error", "response", empty_list);
 
            NormalisedError error = mapper.mapBusinessError(body);
 
            assertThat(error.code()).isEqualTo("validation_error");
            assertThat(error.message()).isEqualTo("The operation was rejected by Atracio.");
        }
    }
 
    // -------------------------------------------------------------------------
    // Local exceptions (timeout / connection failure)
    // -------------------------------------------------------------------------
 
    @Nested
    class LocalExceptions {
 
        @Test
        void shouldMapTimeoutException() {
            NormalisedError error = mapper.mapException(new TimeoutException("Read timed out"));
            assertThat(error.code()).isEqualTo("timeout");
            assertThat(error.backendStatus()).isEqualTo(-1);
        }
 
        @Test
        void shouldMapGenericExceptionToBackendError() {
            NormalisedError error = mapper.mapException(new RuntimeException("Connection refused"));
            assertThat(error.code()).isEqualTo("backend_error");
        }
    }
 
    // -------------------------------------------------------------------------
    // User-facing messages
    // -------------------------------------------------------------------------
 
    @Nested
    class Messages {
 
        @Test
        void unauthorizedMessageContainRightInstructions() {
            NormalisedError error = mapper.map(401,
                    Map.of("code", "access_token_expired", "message", "Expired."));
            assertThat(error.message()).contains("log in again");
        }
 
        @Test
        void validationErrorPreservesBackendMessage() {
            NormalisedError error = mapper.map(400,
                    Map.of("message", "Field 'client' is required."));
            assertThat(error.message()).isEqualTo("Field 'client' is required.");
        }
 
        @Test
        void notFoundPreservesBackendMessage() {
            NormalisedError error = mapper.map(404,
                    Map.of("message", "SalesOrder with id 999 not found."));
            assertThat(error.message()).isEqualTo("SalesOrder with id 999 not found.");
        }
 
        @Test
        void notFoundFallbackWhenNoMessage() {
            NormalisedError error = mapper.map(404, Map.of());
            assertThat(error.message()).isEqualTo("The requested document or entity not found.");
        }
    }
 
    // -------------------------------------------------------------------------
    // NormalisedError convenience predicates
    // -------------------------------------------------------------------------
 
    @Nested
    class Predicates {
 
        @Test
        void isUnauthorized() {
            assertThat(mapper.map(401, Map.of("code", "access_token_expired")).isUnauthorized()).isTrue();
            assertThat(mapper.map(403, null).isUnauthorized()).isFalse();
        }
 
        @Test
        void isNotFound() {
            assertThat(mapper.map(404, null).isNotFound()).isTrue();
            assertThat(mapper.map(401, null).isNotFound()).isFalse();
        }
 
        @Test
        void isTenantMismatch() {
            assertThat(mapper.map(401, Map.of("code", "tenant_mismatch")).isTenantMismatch()).isTrue();
            assertThat(mapper.map(401, Map.of("code", "access_token_expired")).isTenantMismatch()).isFalse();
        }
    }
}