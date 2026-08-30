package com.airtribe.tasktracker.user;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetAccessToken(String email) throws Exception {
        String body = """
                {"name":"Frank","email":"%s","password":"supersecret1"}
                """.formatted(email);
        String responseJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(responseJson);
        return node.get("data").get("accessToken").asText();
    }

    @Test
    void getMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMeReturnsCurrentUser() throws Exception {
        String token = registerAndGetAccessToken("frank@example.com");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("frank@example.com"))
                .andExpect(jsonPath("$.data.name").value("Frank"));
    }

    @Test
    void putMeUpdatesNameAndAvatar() throws Exception {
        String token = registerAndGetAccessToken("frank2@example.com");
        String updateBody = """
                {"name":"Franklin","avatarUrl":"https://img/avatar.png"}
                """;

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Franklin"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://img/avatar.png"));
    }
}
