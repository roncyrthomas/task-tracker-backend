package com.airtribe.tasktracker.ai;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

public class ClaudeAiService implements AiService {

    private final AiProperties properties;
    private final WebClient webClient;

    public ClaudeAiService(AiProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    @Override
    public String generateDescription(String title, String notes) {
        String prompt = "Write a clear, concise task description (2-4 sentences) for a task titled \""
                + title + "\"." + (notes == null || notes.isBlank() ? "" : " Additional notes: " + notes);

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "max_tokens", 300,
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        Map<?, ?> response = webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", properties.getAnthropicApiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return extractText(response);
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response) {
        if (response == null) {
            return "";
        }
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            return "";
        }
        Object text = content.get(0).get("text");
        return text == null ? "" : text.toString();
    }
}
