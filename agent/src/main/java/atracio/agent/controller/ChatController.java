package atracio.agent.controller;

import atracio.agent.agent.AgentOrchestrator;
import atracio.agent.agent.ConversationService;
import atracio.agent.dto.ChatRequest;
import atracio.agent.dto.ChatResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP entry point for the Atracio Agent.
 *
 * Exposes two endpoints:
 *
 *   POST /chat
 *     Receives a user message + context, returns the agent reply.
 *     The bearer token must be provided in the request body (not as a header)
 *     so that the caller controls which Atracio session is used.
 *
 *   DELETE /chat/{conversationId}
 *     Clears the in-memory history for a conversation.
 *     Useful for "reset" commands from the UI.
 *
 * CORS is open for local development.
 * In production, restrict allowedOrigins to the actual UI domain.
 */
@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AgentOrchestrator   orchestrator;
    private final ConversationService conversationService;

    public ChatController(AgentOrchestrator orchestrator,
                          ConversationService conversationService) {
        this.orchestrator        = orchestrator;
        this.conversationService = conversationService;
    }

    // -------------------------------------------------------------------------
    // POST /chat
    // -------------------------------------------------------------------------

    /**
     * Process one user turn and return the agent reply.
     *
     * Request body:
     * {
     *   "userMessage":    "Show me draft sales orders for ACME",
     *   "conversationId": "uuid-generated-by-client",
     *   "tenant":         "demo",
     *   "bearerToken":    "eyJ..."
     * }
     *
     * Response body:
     * {
     *   "assistantMessage": "I found 2 draft sales orders for ACME: ...",
     *   "conversationId":   "uuid-generated-by-client",
     *   "toolUsed":         "document.search",
     *   "toolSuccess":      true
     * }
     */
    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        log.info("POST /chat conversationId={} tenant={}",
                request.getConversationId(), request.getTenant());

        try {
            ChatResponse response = orchestrator.chat(request);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            log.warn("POST /chat bad request — {}", ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));

        } catch (Exception ex) {
            log.error("POST /chat unexpected error", ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred. Please try again."));
        }
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations() {
        log.info("GET /conversations");

        try {
            ConcurrentHashMap<String,List<Map<String,Object>>> conversations = conversationService.getHistories();
            return ResponseEntity.ok(conversations);

        } catch (Exception ex) {
            log.error("GET /conversations", ex);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred. Please try again."));
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /chat/{conversationId}
    // -------------------------------------------------------------------------

    /**
     * Clear the conversation history for a given conversationId.
     *
     * Response: 204 No Content on success.
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> clearConversation(
            @PathVariable String conversationId) {
        log.info("DELETE /chat/{}", conversationId);
        conversationService.clear(conversationId);
        return ResponseEntity.noContent().build();
    }
}