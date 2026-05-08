package atracio.agent.tools;

import atracio.agent.provider.LlmProvider.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Dispatches an LLM tool call to the correct ToolExecutor method.
 *
 * The orchestrator calls dispatch() with the ToolCall from the LLM.
 * This class extracts the typed arguments and routes to the right method.
 *
 * Responsibilities:
 *   - Map tool name → ToolExecutor method
 *   - Extract and cast arguments safely
 *   - Return tool_mapping_error if arguments are missing or unrecognised
 *
 * Never add business logic here — just routing and argument extraction.
 */
@Component
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final ToolExecutor executor;

    public ToolDispatcher(ToolExecutor executor) {
        this.executor = executor;
    }

    /**
     * Dispatches a tool call from the LLM to the correct ToolExecutor method.
     *
     * @param toolCall    the tool call from the LLM (name + arguments)
     * @param tenant      tenant identifier
     * @param bearerToken user access token
     * @return ToolResponse from the executed tool
     */
    public ToolResponse dispatch(ToolCall toolCall, String tenant, String bearerToken) {
        String              name = toolCall.getName();
        Map<String, Object> args = toolCall.getArguments();

        log.debug("ToolDispatcher: dispatching tool='{}' args={}", name, args);

        return switch (name) {

            case "document.search" -> executor.documentSearch(
                    str(args, "entity"),
                    str(args, "filter"),
                    intVal(args, "page", 0),
                    intVal(args, "size", 20),
                    list(args, "sort"),
                    map(args, "entityFilters"),
                    list(args, "fieldsToFetch"),
                    tenant,
                    bearerToken
            );

            case "document.get_details" -> executor.documentGetDetails(
                    str(args, "entity"),
                    longVal(args, "id"),
                    tenant,
                    bearerToken
            );

            case "document.save_draft" -> executor.documentSaveDraft(
                    str(args, "entity"),
                    map(args, "document"),
                    listOfMaps(args, "customFieldValues"),
                    tenant,
                    bearerToken
            );

            case "document.apply_process_action" -> executor.documentApplyProcessAction(
                    str(args, "entity"),
                    longVal(args, "id"),
                    str(args, "action"),
                    map(args, "payload"),
                    tenant,
                    bearerToken
            );

            case "wms.get_article_stock_summary" -> executor.wmsGetArticleStockSummary(
                    longVal(args, "articleId"),
                    longOrNull(args, "siteId"),
                    tenant,
                    bearerToken
            );

            case "wms.lookup_inventory_unit" -> executor.wmsLookupInventoryUnit(
                    str(args, "lookupType"),
                    str(args, "value"),
                    tenant,
                    bearerToken
            );

            case "partner.get_summary" -> executor.partnerGetSummary(
                    str(args, "partnerType"),
                    longVal(args, "partnerId"),
                    tenant,
                    bearerToken
            );

            default -> {
                log.warn("ToolDispatcher: unknown tool '{}'", name);
                yield ToolResponse.agentError("Unknown tool: '" + name + "'. This tool is not registered.");
            }
        };
    }

    // -------------------------------------------------------------------------
    // Safe argument extractors
    // -------------------------------------------------------------------------

    private String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v instanceof String s ? s : null;
    }

    private int intVal(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Integer i) return i;
        if (v instanceof Number  n) return n.intValue();
        return defaultValue;
    }

    private long longVal(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Long    l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof Number  n) return n.longValue();
        return 0L;
    }

    private Long longOrNull(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Long    l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof Number  n) return n.longValue();
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v instanceof List<?> l ? (List<String>) l : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v instanceof List<?> l ? (List<Map<String, Object>>) l : null;
    }
}