package atracio.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("ollama")
public class OllamaChatClientConfig {

    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

} 