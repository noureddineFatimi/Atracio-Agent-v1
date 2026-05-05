package atracio.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.wavefront.WavefrontProperties.Application;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import atracio.agent.agent.AgentOrchestrator;
import atracio.agent.tools.ToolDefinitionRegistry;

import java.util.Scanner;

@Component
public class ConsoleRunner implements CommandLineRunner {

    @Autowired
    private ConfigurableApplicationContext context;

    private final AgentOrchestrator agentOrchestrator;

    public ConsoleRunner(AgentOrchestrator agentOrchestrator) {
            this.agentOrchestrator = agentOrchestrator;
        }

    @Override
    public void run(String... args) {
        
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Atracio Agent Console ===");
        System.out.println("Type 'exit' to quit");

        while (true) {
            System.out.print("\nUser: ");
            String input = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(input)) {
                System.out.println("Exiting...");
                break;
            }

            try {
                String response = agentOrchestrator.chat(input);
                System.out.println("Assistant: " + response);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        scanner.close();
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}