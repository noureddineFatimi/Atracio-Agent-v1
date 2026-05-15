package atracio.agent.tools;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ToolShemas {
    
        private final Map<String, Object> documentSearchToolShema = tool("document.search",
                """
                Search and list documents in Atracio across Sales, Procurement, and WMS modules.
                Use this tool when the user wants to find, list, or filter documents such as
                SalesOrder, Quotation, PurchaseOrder, PurchaseRequest, SalesInvoice, PurchaseInvoice,
                StockTransferOrder, StockAdjustment, or InventoryCount.
                Always specify the entity. Use filter for free-text search.
                Use entityFilters for structured criteria (e.g. client id, lifecycle state).
                """,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "entity", Map.of(
                                        "type", "string",
                                        "description",
                                        "Atracio entity key",
                                        "enum", List.of("SalesOrder", "Quotation", "SalesInvoice", "PurchaseOrder", "PurchaseRequest", "PurchaseInvoice" ,"StockTransferOrder", "StockAdjustment", "InventoryCount")
                                ),
                                "filter", Map.of(
                                        "type", "string",
                                        "description", "Free-text search string applied across indexed fields."
                                ),
                                "page", Map.of(
                                        "type", "integer",
                                        "description", "0-based page number. Default 0.",
                                        "default", 0
                                ),
                                "size", Map.of(
                                        "type", "integer",
                                        "description", "Number of results per page. Default 20, max 100.",
                                        "default", 20
                                ),
                                "sort", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "description",
                                        "Sort directives. Format: 'field,direction'. " +
                                        "Example: ['documentNumber,desc', 'documentDate,asc']."
                                ),
                                "entityFilters", Map.of(
                                        "type", "object",
                                        "description",
                                        "Structured filter criteria. Keys are Atracio field paths. " +
                                        "Example: {\"client.id\": 44, \"lifecycle.lifecycleState\": \"DRAFT\"}."
                                ),
                                "fieldsToFetch", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "description",
                                        "Restrict response to these fields. Omit for full response. " +
                                        "Example: ['id', 'documentNumber', 'lifecycle.lifecycleState']."
                                )
                        ),
                        "required", List.of("entity")
                )
        );

        private final Map<String, Object> documentSaveDraftToolShema = tool("document.save_draft",
                """
                Create or update a draft document in Atracio.
                V1 rule: always call document.get_details first to retrieve the existing document,
                modify only the fields the user requested, then call this tool with the full object.
                Do not invent or omit fields — send back what you received from get_details.
                Only DRAFT documents can be modified.
                """,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "entity", Map.of(
                                        "type", "string",
                                        "description",
                                        "Atracio entity key",
                                        "enum", List.of("SalesOrder", "Quotation", "SalesInvoice", "PurchaseOrder", "PurchaseRequest", "PurchaseInvoice" ,"StockTransferOrder", "StockAdjustment", "InventoryCount")
                                ),
                                "document", Map.of(
                                        "type", "object",
                                        "description",
                                        "The full document payload. Must include id for updates. " +
                                        "Fetch with document.get_details first, then modify target fields only."
                                ),
                                "customFieldValues", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "object"),
                                        "description", "Optional list of custom field values. Omit if not needed."
                                )
                        ),
                        "required", List.of("entity", "document")
                )
        );
        
        private final Map<String, Object> documentGetDetailsToolShema = tool("document.get_details",
                """
                Retrieve the full details of a single Atracio document by its entity type and id.
                """,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "entity", Map.of(
                                        "type", "string",
                                        "description",
                                        "Atracio entity key",
                                        "enum", List.of("SalesOrder", "Quotation", "SalesInvoice", "PurchaseOrder", "PurchaseRequest", "PurchaseInvoice" ,"StockTransferOrder", "StockAdjustment", "InventoryCount")
                                ),
                                "id", Map.of(
                                        "type", "integer",
                                        "description", "Primary key of the document."
                                )
                        ),
                        "required", List.of("entity", "id")
                )
        );

        private final Map<String, Object> wmsGetArticleStockSummaryToolShema = tool("wms.get_article_stock_summary",
                    """
                    Return a consolidated stock summary for a specific article.
                    Aggregates: current stock quantity, forecast quantity, stock valuation,
                    entries and issues for the current year, and annual turnover.
                    Use this when the user asks about stock level, inventory, or article quantities.
                    Optionally filter by site with siteId.
                    """,
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "articleId", Map.of(
                                            "type", "integer",
                                            "description", "Primary key of the article."
                                    ),
                                    "siteId", Map.of(
                                            "type", "integer",
                                            "description",
                                            "Optional. Filter stock data by warehouse site. Omit for all sites."
                                    )
                            ),
                            "required", List.of("articleId")
                    )
            );

        private final Map<String, Object> wmsLookupInventoryUnitToolShema = tool("wms.lookup_inventory_unit",
                """
                Find an inventory unit by scanning a barcode, RFID tag, or serial number.
                Returns the article, warehouse location, and current quantity for that unit.
                Use this when the user scans a physical item or provides a tracking identifier.
                """,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "lookupType", Map.of(
                                        "type", "string",
                                        "enum", List.of("barcode", "rfidTag", "serialNumber"),
                                        "description", "The type of identifier provided."
                                ),
                                "value", Map.of(
                                        "type", "string",
                                        "description", "The identifier value to look up."
                                )
                        ),
                        "required", List.of("lookupType", "value")
                )
        );

        private final Map<String, Object> documentApplyProcessActionToolShma = tool("document.apply_process_action",
                """
                Apply a process action to an Atracio document to transition it through its lifecycle.
                Use this to release, approve, post, cancel, or complete a document.
                The action string follows the format 'family.verb'.
                Supported actions:
                  lifecycle : release, close, cancel
                  approval  : submit, approve
                  posting   : post, reverse
                  execution : start, complete
                  payment   : allocate
                """,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "entity", Map.of(
                                        "type", "string",
                                        "description",
                                        "Atracio entity key",
                                        "enum", List.of("SalesOrder", "Quotation", "SalesInvoice", "PurchaseOrder", "PurchaseRequest", "PurchaseInvoice" ,"StockTransferOrder", "StockAdjustment", "InventoryCount")
                                ),
                                "id", Map.of(
                                        "type", "integer",
                                        "description", "Primary key of the document."
                                ),
                                "action", Map.of(
                                        "type", "string",
                                        "description",
                                        "Compound action string. Format: 'family.verb'. " +
                                        "Examples: 'lifecycle.release', 'approval.approve', 'posting.post'.",
                                        "enum", List.of("lifecycle.release", "lifecycle.close", "lifecycle.cancel", "approval.submit", "approval.approve", "posting.post", "posting.reverse", "execution.start", "execution.complete", "payment.allocate")
                                ),
                                "payload", Map.of(
                                        "type", "object",
                                        "description",
                                        "Optional request body for actions that require extra data " +
                                        "(e.g. payment.allocate). Omit or set null for most actions."
                                )
                        ),
                        "required", List.of("entity", "id", "action")
                )
        );

        private final Map<String, Object> partnerGetSummaryToolShema = tool("partner.get_summary",
                """
                Return a commercial summary for a client or vendor partner.
                For clients: turnover, unpaid amount, unpaid invoices, last invoice date,
                  sales orders count, last sales order.
                For vendors: turnover, unpaid amount, unpaid invoices, last purchase order date.
                Use this when the user asks about a client's or vendor's financial situation,
                activity, or outstanding balances.
                """,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "partnerType", Map.of(
                                        "type", "string",
                                        "enum", List.of("client", "vendor"),
                                        "description", "Whether the partner is a client or a vendor."
                                ),
                                "partnerId", Map.of(
                                        "type", "integer",
                                        "description", "Primary key of the client or vendor."
                                )
                        ),
                        "required", List.of("partnerType", "partnerId")
                )
        );

        public final Map<String, Object> getDocumentSearchToolShema(){
                return documentSearchToolShema;
        } 

        public final Map<String, Object> getDocumentSaveDraftToolShema() {
                return documentSaveDraftToolShema;
        }

        public final Map<String, Object> getDocumentGetDetailsToolShema() {
                return documentGetDetailsToolShema;
        }

        public final Map<String, Object> getWmsGetArticleStockSummaryToolShema() {
                return wmsGetArticleStockSummaryToolShema;
        }

        public final Map<String, Object> getWmsLookupInventoryUnitToolShema() {
                return wmsLookupInventoryUnitToolShema;
        }

        public final Map<String, Object> getDocumentApplyProcessActionToolShema() {
                return documentApplyProcessActionToolShma;
        }

        public final Map<String, Object> getPartnerGetSummaryToolShema() {
                return partnerGetSummaryToolShema;
        }

        private Map<String, Object> tool(String name,
                                     String description,
                                     Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name",        name,
                        "description", description.strip(),
                        "parameters",  parameters
                )
        );
    }
}
