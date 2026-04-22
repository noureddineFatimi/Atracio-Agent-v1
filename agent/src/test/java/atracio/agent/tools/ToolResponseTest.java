package atracio.agent.tools;

import atracio.agent.atracio.AtracioErrorMapper;
import atracio.agent.atracio.AtracioErrorMapper.NormalisedError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResponseTest {

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    void successResponse() {
        Map<String, Object> data = Map.of("id", 101, "documentNumber", "SO-000101");

        ToolResponse response = ToolResponse.success(
                "document.search", data, "demo", "/entities/list/SalesOrder");

        assertThat(response.isOk()).isTrue();
        assertThat(response.getTool()).isEqualTo("document.search");
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getError()).isNull();
        assertThat(response.getMeta()).containsEntry("tenant", "demo");
        assertThat(response.getMeta()).containsEntry("backendPath", "/entities/list/SalesOrder");
    }

    // -------------------------------------------------------------------------
    // Error from NormalisedError
    // -------------------------------------------------------------------------

    @Test
    void errorResponseFromNormalisedError() {
        AtracioErrorMapper mapper = new AtracioErrorMapper();
        NormalisedError normalisedError = mapper.map(
                401, Map.of("code", "access_token_expired", "message", "Token expired."));

        ToolResponse response = ToolResponse.error(
                "document.search", normalisedError, "demo", "/entities/list/SalesOrder");

        assertThat(response.isOk()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().code()).isEqualTo("unauthorized");
        assertThat(response.getError().backendStatus()).isEqualTo(401);
        assertThat(response.getError().backendCode()).isEqualTo("access_token_expired");
        assertThat(response.getMeta()).containsEntry("tenant", "demo");
    }

    // -------------------------------------------------------------------------
    // Tool-level error (no backend call)
    // -------------------------------------------------------------------------

    @Test
    void toolErrorResponse() {
        ToolResponse response = ToolResponse.toolError(
                "document.search", "tool_mapping_error", "Unknown entity: FooBar");

        assertThat(response.isOk()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError().code()).isEqualTo("tool_mapping_error");
        assertThat(response.getError().message()).isEqualTo("Unknown entity: FooBar");
        assertThat(response.getError().backendStatus()).isEqualTo(-1);
        assertThat(response.getError().backendCode()).isNull();
        assertThat(response.getMeta()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Success with meta
    // -------------------------------------------------------------------------

    @Test
    void toolSuccessWithMeta() {
        Map<String, Object> data = Map.of("id", 101, "documentNumber", "SO-000101");
        Map<String, Object> meta = Map.of(
                    "tenant",      "demo",
                    "aggregated",  true
        );
        ToolResponse response = ToolResponse.successWithMeta("document.search", data, meta);
        assertThat(response.isOk()).isTrue();
        assertThat(response.getTool()).isEqualTo("document.search");
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getError()).isNull();
        assertThat(response.getMeta()).containsEntry("tenant", "demo");
        assertThat(response.getMeta()).containsEntry("aggregated", true);
    }

    // -------------------------------------------------------------------------
    // Null-safety
    // -------------------------------------------------------------------------

    @Test
    void nullTenantAndPathDoNotThrow() {
        ToolResponse response = ToolResponse.success("document.get_details", null, null, null);

        assertThat(response.isOk()).isTrue();
        assertThat(response.getMeta()).containsEntry("tenant", "");
        assertThat(response.getMeta()).containsEntry("backendPath", "");
    }
}