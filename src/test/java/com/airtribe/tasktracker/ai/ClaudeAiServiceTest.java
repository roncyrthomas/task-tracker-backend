package com.airtribe.tasktracker.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeAiServiceTest {

    @Test
    void extractsGeneratedTextFromAnthropicResponse() {
        String fakeJson = """
                {"content":[{"type":"text","text":"A generated description."}]}
                """;
        WebClient stubbedClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(fakeJson)
                        .build()))
                .build();

        AiProperties properties = new AiProperties();
        properties.setAnthropicApiKey("test-key");
        properties.setModel("claude-fable-5");

        ClaudeAiService service = new ClaudeAiService(properties, stubbedClient);

        String result = service.generateDescription("Write onboarding doc", "cover setup and FAQ");

        assertThat(result).isEqualTo("A generated description.");
    }
}
