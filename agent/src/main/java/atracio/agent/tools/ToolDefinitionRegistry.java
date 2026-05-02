package atracio.agent.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registry of all tool definitions exposed to the LLM.
 *
 * The orchestrator passes getAll() directly to the LLM API as the "tools" array.
 * Each definition follows the OpenAI / Spring AI function-calling contract:
 *
 *   {
 *     "type": "function",
 *     "function": {
 *       "name":        "document.search",
 *       "description": "...",
 *       "parameters":  { JSON Schema }
 *     }
 *   }
 *
 * Rules:
 *   - Names match exactly the tool names used in ToolExecutor
 *   - Descriptions are written for the LLM, not for humans — they guide tool selection
 *   - Parameters follow JSON Schema draft-07
 *   - Required fields are explicit — the LLM must not guess missing values
 */
@Component
public class ToolDefinitionRegistry {

    private final List<Map<String, Object>> tools;
    private final ToolShemas toolShemas;

    public ToolDefinitionRegistry(ToolShemas toolShemas) {
        this.toolShemas = toolShemas;
        this.tools = List.of(
                documentSearch(),
                documentGetDetails(),
                documentSaveDraft(),
                documentApplyProcessAction(),
                wmsGetArticleStockSummary(),
                wmsLookupInventoryUnit(),
                partnerGetSummary()
        );
    }

    /**
     * Returns all tool definitions ready to be sent to the LLM.
     */
    public List<Map<String, Object>> getAll() {
        return tools;
    }

    /**
     * Returns a single tool definition by name, or null if not found.
     */
    public Map<String, Object> getByName(String name) {
        return tools.stream()
                .filter(t -> name.equals(functionName(t)))
                .findFirst()
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // 1. document.search
    // -------------------------------------------------------------------------

    private Map<String, Object> documentSearch() {
        return toolShemas.getDocumentSearchToolShema();
    }

    // -------------------------------------------------------------------------
    // 2. document.get_details
    // -------------------------------------------------------------------------

    private Map<String, Object> documentGetDetails() {
        return toolShemas.getDocumentGetDetailsToolShema();
    }

    // -------------------------------------------------------------------------
    // 3. document.save_draft
    // -------------------------------------------------------------------------

    private Map<String, Object> documentSaveDraft() {
        return toolShemas.getDocumentSaveDraftToolShema();
    }

    // -------------------------------------------------------------------------
    // 4. document.apply_process_action
    // -------------------------------------------------------------------------

    private Map<String, Object> documentApplyProcessAction() {
        return toolShemas.getDocumentApplyProcessActionToolShema();
    }

    // -------------------------------------------------------------------------
    // 5. wms.get_article_stock_summary
    // -------------------------------------------------------------------------

    private Map<String, Object> wmsGetArticleStockSummary() {
        return toolShemas.getWmsGetArticleStockSummaryToolShema();
    }

    // -------------------------------------------------------------------------
    // 6. wms.lookup_inventory_unit
    // -------------------------------------------------------------------------

    private Map<String, Object> wmsLookupInventoryUnit() {
        return toolShemas.getWmsLookupInventoryUnitToolShema();
    }

    // -------------------------------------------------------------------------
    // 7. partner.get_summary
    // -------------------------------------------------------------------------

    private Map<String, Object> partnerGetSummary() {
        return toolShemas.getPartnerGetSummaryToolShema();
    }

    // -------------------------------------------------------------------------
    // Builder helper
    // -------------------------------------------------------------------------

    private String functionName(Map<String, Object> toolDef) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) toolDef.get("function");
        return fn != null ? (String) fn.get("name") : null;
    }

    public static List<ToolCallback> getToolsCallBackList(List<Map<String, Object>> toolsShemasMap) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<ToolCallback> toolsCallBack = new ArrayList<>();
        try {
            for (Map<String,Object> toolDefinitionShema : toolsShemasMap) {
                Object functionObject = toolDefinitionShema.get("function");

                String name = functionObject instanceof Map<?, ?> fO ? fO.get("name") instanceof String n ? n : "" : "";
                String description = functionObject instanceof Map<?, ?> fO ? fO.get("description") instanceof String d ? d : "" : "";
                Map<?, ?> inputSchemaObject = functionObject instanceof Map<?, ?> fO ? fO.get("parameters") instanceof Map<?, ?> inputSchemaMap ? inputSchemaMap : Map.of() : Map.of();
                String inputSchema = objectMapper.writeValueAsString(inputSchemaObject);

                ToolCallback toolCallBack = toToolCallBack(name, description, inputSchema);

                toolsCallBack.add(toolCallBack);
            }
            return toolsCallBack;
        } catch (Exception e) {
            System.out.println(e);
            return toolsCallBack;
        }
    }

    private final static ToolCallback toToolCallBack(String name, String description, String inputSchema) {
        return FunctionToolCallback
                .builder(name, (String input) -> "not_executed") // no-op, exécution manuelle
                .description(description)
                .inputSchema(inputSchema)
                .inputType(String.class)
                .build();
    }
}