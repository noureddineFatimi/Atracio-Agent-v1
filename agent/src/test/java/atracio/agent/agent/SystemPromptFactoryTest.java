package atracio.agent.agent;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptFactoryTest {

    private final SystemPromptFactory factory = new SystemPromptFactory();
    private static final Logger log = LoggerFactory.getLogger(SystemPromptFactoryTest.class);

    @Test
    void shouldContainTenantName() {
        String prompt = factory.build();
        assertThat(prompt).contains("tenant: demo");
    }

    @Test
    void shouldContainAllSevenToolNames() {
        String prompt = factory.build();
        assertThat(prompt).contains("document.search");
        assertThat(prompt).contains("document.get_details");
        assertThat(prompt).contains("document.save_draft");
        assertThat(prompt).contains("document.apply_process_action");
        assertThat(prompt).contains("wms.get_article_stock_summary");
        assertThat(prompt).contains("wms.lookup_inventory_unit");
        assertThat(prompt).contains("partner.get_summary");
    }

    @Test
    void shouldContainAllThreeModules() {
        String prompt = factory.build();
        assertThat(prompt).containsIgnoringCase("SALES MODULE");
        assertThat(prompt).containsIgnoringCase("PROCUREMENT MODULE");
        assertThat(prompt).containsIgnoringCase("WMS");
    }

    @Test
    void shouldContainAllErrorCodes() {
        String prompt = factory.build();
        assertThat(prompt).contains("unauthorized");
        assertThat(prompt).contains("forbidden");
        assertThat(prompt).contains("tenant_mismatch");
        assertThat(prompt).contains("validation_error");
        assertThat(prompt).contains("not_found");
        assertThat(prompt).contains("timeout");
        assertThat(prompt).contains("backend_error");
        assertThat(prompt).contains("tool_mapping_error");
    }

    @Test
    void shouldContainKeyBehaviouralRules() {
        String prompt = factory.build();
        assertThat(prompt).contains("NEVER invent business data");
        assertThat(prompt).contains("NEVER re-implement business logic");
        assertThat(prompt).contains("document.get_details before save_draft");
    }

    @Test
    void shouldNotBeBlank() {
        assertThat(factory.build()).isNotBlank();
    }

    @Test 
    void showSystemPrompt() {
        log.info("System prompt= {}", factory.build());
    }
}