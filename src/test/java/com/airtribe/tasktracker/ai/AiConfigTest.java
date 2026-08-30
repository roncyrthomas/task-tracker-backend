package com.airtribe.tasktracker.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigTest {

    @Test
    void choosesNoOpWhenApiKeyBlank() {
        AiProperties properties = new AiProperties();
        properties.setAnthropicApiKey("");

        AiService service = new AiConfig().aiService(properties, WebClient.builder());

        assertThat(service).isInstanceOf(NoOpAiService.class);
    }

    @Test
    void choosesClaudeWhenApiKeyPresent() {
        AiProperties properties = new AiProperties();
        properties.setAnthropicApiKey("real-key");

        AiService service = new AiConfig().aiService(properties, WebClient.builder());

        assertThat(service).isInstanceOf(ClaudeAiService.class);
    }
}
