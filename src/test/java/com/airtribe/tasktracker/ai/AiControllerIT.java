package com.airtribe.tasktracker.ai;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"User","email":"%s","password":"supersecret1"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    @Test
    void generateDescriptionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/tasks/ai/generate-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write changelog"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generateDescriptionReturnsNoOpFallbackWhenNoApiKeyConfigured() throws Exception {
        String token = register("aiuser@example.com");

        mockMvc.perform(post("/api/tasks/ai/generate-description")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write changelog","notes":"summarize v2 release"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value(org.hamcrest.Matchers.containsString("Write changelog")));
    }

    @Test
    void generateDescriptionRejectsBlankTitle() throws Exception {
        String token = register("aiuser2@example.com");

        mockMvc.perform(post("/api/tasks/ai/generate-description")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
