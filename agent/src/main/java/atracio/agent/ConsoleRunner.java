package atracio.agent;

import atracio.agent.agent.AgentOrchestrator;
import atracio.agent.dto.ChatRequest;
import atracio.agent.dto.ChatResponse;
import atracio.agent.dto.ToolCallDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Scanner;
import java.util.UUID;

/**
 * Console runner for local development and testing.
 *
 * Starts automatically when the application is ready and runs in a
 * dedicated daemon thread alongside the embedded web server.
 *
 * Phase 4: wired to AgentOrchestrator — no longer a stub.
 *
 * Usage:
 *   1. mvn spring-boot:run
 *   2. Enter your Atracio bearer token when prompted (stored in memory for the session only)
 *   3. Type any message and press Enter
 *   4. Type 'exit' or 'quit' to stop the console runner (server keeps running)
 *   5. Type 'reset' to clear the current conversation history
 */
@Component
public class ConsoleRunner {

    private static final Logger log = LoggerFactory.getLogger(ConsoleRunner.class);

    private final AgentOrchestrator orchestrator;

    public ConsoleRunner(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread thread = new Thread(this::runLoop, "console-runner");
        thread.setDaemon(true);
        thread.start();
        log.info("Console runner started.");
    }

    private void runLoop() {
        Scanner scanner = new Scanner(System.in);

        printBanner();

        // Each console session gets a stable conversationId
        String conversationId = "console-" + UUID.randomUUID().toString().substring(0, 8);
        String tenant         = "demo";

        // Prompt for bearer token once at startup
        System.out.print("Atracio Bearer Token: ");
        String bearerToken = scanner.hasNextLine() ? scanner.nextLine().trim() : "";

        if (bearerToken.isBlank()) {
            System.out.println("[warn] No token provided — tool calls will fail with 'unauthorized'.");
            bearerToken = "no-token";
        }

        System.out.println();
        System.out.println("Session started. conversationId=" + conversationId);
        System.out.println("Type 'reset' to clear history, 'exit' to quit.");
        System.out.println();

        while (true) {
            System.out.print("You: ");

            if (!scanner.hasNextLine()) break;

            String input = scanner.nextLine().trim();

            if (input.isBlank()) continue;

            if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                System.out.println("Console runner stopped.");
                log.info("Console runner stopped by user.");
                break;
            }

            if ("reset".equalsIgnoreCase(input)) {
                conversationId = "console-" + UUID.randomUUID().toString().substring(0, 8);
                System.out.println("[reset] New conversation started. id=" + conversationId);
                System.out.println();
                continue;
            }

            try {
                ChatRequest  request  = new ChatRequest(input, conversationId, tenant, bearerToken);
                ChatResponse response = orchestrator.chat(request);

                System.out.println();
                if (response.getToolCalls() != null) {
                    for (ToolCallDto toolCallDto : response.getToolCalls()) {
                        String status = toolCallDto.status() == "success" ? "success" : "error";
                        System.out.println("Tool    : " + status + " in execution tool " + toolCallDto.tool());
                    }
                }
                System.out.println("Agent   : " + response.getAssistantMessage());
                System.out.println();

            } catch (Exception ex) {
                System.out.println("[error] " + ex.getMessage());
                log.error("Console runner error", ex);
                System.out.println();
            }
        }
        scanner.close();
    }

    private void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   Atracio Agent — Console Runner         ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();
    }
}