package com.airtribe.tasktracker.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpAiServiceTest {

    @Test
    void returnsClearUnavailableMessageContainingTheTitle() {
        NoOpAiService service = new NoOpAiService();

        String result = service.generateDescription("Migrate database", "some notes");

        assertThat(result.toLowerCase()).contains("unavailable");
        assertThat(result).contains("Migrate database");
    }
}
