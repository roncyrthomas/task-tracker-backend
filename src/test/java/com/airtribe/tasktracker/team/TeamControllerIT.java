package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetAccessToken(String email, String name) throws Exception {
        String body = """
                {"name":"%s","email":"%s","password":"supersecret1"}
                """.formatted(name, email);
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    @Test
    void ownerCanCreateTeamAndSeeItInMyTeamsAndMembers() throws Exception {
        String ownerToken = registerAndGetAccessToken("owner1@example.com", "Owner One");

        String createBody = """
                {"name":"Engineering","description":"The eng team"}
                """;
        String createJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Engineering"))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(createJson).get("data").get("id").asText();

        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Engineering"));

        mockMvc.perform(get("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"));
    }

    @Test
    void nonMemberCannotAccessTeam() throws Exception {
        String ownerToken = registerAndGetAccessToken("owner2@example.com", "Owner Two");
        String outsiderToken = registerAndGetAccessToken("outsider@example.com", "Outsider");

        String createBody = """
                {"name":"Design","description":"The design team"}
                """;
        String createJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(createJson).get("data").get("id").asText();

        mockMvc.perform(get("/api/teams/" + teamId).header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }
}
