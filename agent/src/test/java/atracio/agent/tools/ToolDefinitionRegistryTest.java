package atracio.agent.tools;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;

class ToolDefinitionRegistryTest {

    private final ToolShemas toolShemas = new ToolShemas();
    private final ToolDefinitionRegistry registry = new ToolDefinitionRegistry(toolShemas);
    private static final Logger log = LoggerFactory.getLogger(ToolDefinitionRegistryTest.class);

    @Test
    void shouldExposeAll7Tools() {
        assertThat(registry.getAll()).hasSize(7);
    }

    @Test
    void allToolsShouldHaveCorrectStructure() {
        for (Map<String, Object> tool : registry.getAll()) {
            assertThat(tool).containsKey("type");
            assertThat(tool.get("type")).isEqualTo("function");
            assertThat(tool).containsKey("function");

            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) tool.get("function");
            assertThat(fn).containsKeys("name", "description", "parameters");
            assertThat((String) fn.get("name")).isNotBlank();
            assertThat((String) fn.get("description")).isNotBlank();
        }
    }

    @Test
    void allToolNamesShouldMatchToolExecutorNames() {
        List<String> expectedNames = List.of(
                "document.search",
                "document.get_details",
                "document.save_draft",
                "document.apply_process_action",
                "wms.get_article_stock_summary",
                "wms.lookup_inventory_unit",
                "partner.get_summary"
        );

        List<String> actualNames = registry.getAll().stream()
                .map(t -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fn = (Map<String, Object>) t.get("function");
                    return (String) fn.get("name");
                })
                .toList();

        assertThat(actualNames).containsExactlyElementsOf(expectedNames);
    }

    @Test
    void getByNameShouldReturnCorrectTool() {
        Map<String, Object> tool = registry.getByName("document.search");

        assertThat(tool).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) tool.get("function");
        assertThat(fn.get("name")).isEqualTo("document.search");
    }

    @Test
    void getByNameUnknownShouldReturnNull() {
        assertThat(registry.getByName("unknown.tool")).isNull();
    }

    @Test
    void documentSearchShouldRequireEntityOnly() {
        Map<String, Object> tool = registry.getByName("document.search");
        @SuppressWarnings("unchecked")
        Map<String, Object> fn     = (Map<String, Object>) tool.get("function");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) fn.get("parameters");

        assertThat(params.get("required")).isEqualTo(List.of("entity"));
    }

    @Test 
    void toListDefinition() {
        List<ToolCallback> listDefinition = ToolDefinitionRegistry.getToolsCallBackList(registry.getAll());
        assertThat(listDefinition).hasSize(7);
        log.info("toolsDefinitions = {}", listDefinition);
    }

}