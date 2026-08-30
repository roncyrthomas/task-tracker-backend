package com.airtribe.tasktracker.ai;

public class NoOpAiService implements AiService {

    @Override
    public String generateDescription(String title, String notes) {
        return "AI generation is unavailable (no ANTHROPIC_API_KEY configured). "
                + "Please write a description manually for: " + title;
    }
}
