package com.airtribe.tasktracker.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiConfig {

    @Bean
    public AiService aiService(AiProperties properties, WebClient.Builder webClientBuilder) {
        if (properties.getAnthropicApiKey() == null || properties.getAnthropicApiKey().isBlank()) {
            return new NoOpAiService();
        }
        return new ClaudeAiService(properties, webClientBuilder.build());
    }
}
