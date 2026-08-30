package com.airtribe.tasktracker.comment;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommentControllerIT extends AbstractIntegrationTest {

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

    private String createTeamAndTask(String token) throws Exception {
        String teamJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(teamJson).get("data").get("id").asText();
        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Discuss design","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(taskJson).get("data").get("id").asText();
    }

    @Test
    void memberCanPostAndListComments() throws Exception {
        String token = register("commenter@example.com");
        String taskId = createTeamAndTask(token);

        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Looks good"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.body").value("Looks good"));

        mockMvc.perform(get("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void nonMemberCannotComment() throws Exception {
        String ownerToken = register("commentowner@example.com");
        String taskId = createTeamAndTask(ownerToken);
        String outsiderToken = register("commentoutsider@example.com");

        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Sneaky comment"}
                                """))
                .andExpect(status().isForbidden());
    }
}
