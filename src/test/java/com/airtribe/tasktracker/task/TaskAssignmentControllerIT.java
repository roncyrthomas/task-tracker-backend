package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskAssignmentControllerIT extends AbstractIntegrationTest {

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

    private String createTeam(String token) throws Exception {
        String json = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("id").asText();
    }

    private String inviteAndAccept(String ownerToken, String teamId, String inviteeToken, String inviteeEmail) throws Exception {
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + inviteeEmail + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(inviteJson).get("data").get("token").asText();
        String acceptJson = mockMvc.perform(post("/api/invitations/" + token + "/accept")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(acceptJson).get("data").get("userId").asText();
    }

    @Test
    void statusTransitionAssignmentAndMineFlow() throws Exception {
        String ownerToken = register("assignowner@example.com");
        String teamId = createTeam(ownerToken);
        String memberToken = register("assignee@example.com");
        String memberId = inviteAndAccept(ownerToken, teamId, memberToken, "assignee@example.com");

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ship feature","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskJson).get("data").get("id").asText();

        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + memberId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(memberId));

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/tasks/mine")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Ship feature"));
    }
}
