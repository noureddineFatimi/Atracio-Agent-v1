package atracio.agent.atracio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AtracioBackendClientHttpTest {

    private static final String BASE_URL = "https://demo.prod.atracio.com/api";
    private static final String TOKEN = "fake-token";

    private MockRestServiceServer server;
    private AtracioBackendClientHttp client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        server = MockRestServiceServer.bindTo(builder).build();

        objectMapper = new ObjectMapper();

        RestClient restClient = builder.build();

        AtracioUrlResolver resolver = new AtracioUrlResolver(BASE_URL);

        client = new AtracioBackendClientHttp(
                restClient,
                resolver,
                objectMapper
        );
    }

    // =========================================================================
    // document.search
    // =========================================================================

    @Test
    void shouldListEntities() throws JsonProcessingException{

        String response = """
        {"content":[{"id":101,"documentNumber":"SO-000101"},{"id":191,"documentNumber":"SO-000191"}],"pageable":{"pageNumber":0,"pageSize":20},"totalElements":2,"totalPages":1}""";

        server.expect(requestTo(BASE_URL + "/entities/list/SalesOrder"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.listEntities(
                "SalesOrder",
                Map.of(
                        "filter", "ACME",
                        "page", 0,
                        "size", 10
                ),
                TOKEN
        );

        assertNotNull(result);
        assertEquals(response, objectMapper.writeValueAsString(result));

        server.verify();
    }

    // =========================================================================
    // document.get_details
    // =========================================================================

    @Test
    void shouldGetEntityDetails() throws JsonProcessingException{

        String response = """
        {"id":501,"documentNumber":"PO-000501"}""";

        server.expect(requestTo(BASE_URL + "/entities/details/PurchaseOrder/501"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.getEntityDetails(
                "PurchaseOrder",
                501L,
                TOKEN
        );

        assertEquals(response, objectMapper.writeValueAsString(result));

        server.verify();
    }

    // =========================================================================
    // document.save_draft
    // =========================================================================

    @Test
    void shouldSaveEntity() {

        String response = """
                {
                  "status": "success",
                  "response": {
                    "id": 700,
                    "documentNumber": "QT-000700"
                  }
                }
                """;

        server.expect(requestTo(BASE_URL + "/entities/save/Quotation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.saveEntity(
                "Quotation",
                Map.of(
                        "entity", Map.of(
                                "subject", "Quote for ACME"
                        )
                ),
                TOKEN
        );

        assertEquals("success", result.get("status"));

        server.verify();
    }

    // =========================================================================
    // document.validate
    // =========================================================================

    @Test
    void shouldValidateEntity() {

        String response = """
                [
                  {
                    "field": "subject",
                    "message": "Subject required"
                  }
                ]
                """;

        server.expect(requestTo(BASE_URL + "/entities/validate/Quotation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        List<Map<String, Object>> result = client.validateEntity(
                "Quotation",
                Map.of(),
                TOKEN
        );

        assertEquals(1, result.size());
        assertEquals("subject", result.get(0).get("field"));

        server.verify();
    }

    // =========================================================================
    // document.delete
    // =========================================================================

    @Test
    void shouldDeleteEntity() {

        String response = """
                {
                  "status": "success",
                  "reponse": "null"
                }
                """;

        server.expect(requestTo(BASE_URL + "/entities/delete/SalesOrder/1,2"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.deleteEntity(
                "SalesOrder",
                "1,2",
                TOKEN
        );

        assertEquals("success", result.get("status"));

        server.verify();
    }

    // =========================================================================
    // document.apply_process_action
    // =========================================================================

    @Test
    void shouldApplyLifecycleReleaseAction() throws JsonProcessingException{

        String response = """
                {"entity":"SalesOrder","id":"101","state":"RELEASED"}""";

        server.expect(requestTo(
                        BASE_URL + "/process/lifecycle/SalesOrder/101/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.applyProcessAction(
                "SalesOrder",
                101L,
                "lifecycle.release",
                Map.of(),
                TOKEN
        );

        assertEquals(response, objectMapper.writeValueAsString(result));

        server.verify();
    }

    // =========================================================================
    // WMS
    // =========================================================================

    @Test
    void shouldGetArticleQuantity() {

        String response = "120.5";

        server.expect(requestTo(
                        BASE_URL + "/warehouse/article/quantity/9001?siteId=3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Double quantity = client.getArticleQuantity(
                9001L,
                3L,
                TOKEN
        );

        assertEquals(120.5, quantity);

        server.verify();
    }

    @Test
    void shouldLookupInventoryUnitByBarcode() {

        String response = """
                {
                  "id": 100,
                  "lookupType": "barcode",
                  "value": "ABC-123456",
                  "quantiy": 15.0
                }
                """;

        server.expect(requestTo(
                        BASE_URL + "/warehouse/units/barcode?barcode=ABC-123456"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.lookupInventoryUnit(
                "barcode",
                "ABC-123456",
                TOKEN
        );

        assertEquals("ABC-123456", result.get("value"));

        server.verify();
    }

    // =========================================================================
    // Partner
    // =========================================================================

    @Test
    void shouldGetClientTurnover() {

        String response = "18000.0";

        server.expect(requestTo(
                        BASE_URL + "/client/turnover/44"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Double turnover = client.getClientTurnover(
                44L,
                TOKEN
        );

        assertEquals(18000.0, turnover);

        server.verify();
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    @Test
    void shouldThrowAtracioBackendExceptionOn401() throws JsonProcessingException{

        String errorResponse = """
                {"code":"access_token_expired","message":"Access token expired."}""";

        server.expect(requestTo(BASE_URL + "/entities/details/SalesOrder/1"))
                .andRespond(withUnauthorizedRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorResponse));

        AtracioBackendException ex = assertThrows(
                AtracioBackendException.class,
                () -> client.getEntityDetails(
                        "SalesOrder",
                        1L,
                        TOKEN
                )
        );

        assertEquals(401, ex.getHttpStatus());
        assertEquals(
                errorResponse,
                objectMapper.writeValueAsString(ex.getBody())
        );

        server.verify();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForUnknownLookupType() {

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.lookupInventoryUnit(
                        "unknown",
                        "123",
                        TOKEN
                )
        );

        assertTrue(ex.getMessage().contains("Unknown lookupType"));
    }
}