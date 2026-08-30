package com.airtribe.tasktracker.notification;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerIT extends AbstractIntegrationTest {

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
    void assigningATaskCreatesAPersistedNotificationForTheAssignee() throws Exception {
        String ownerToken = register("notifyowner@example.com");
        String assigneeToken = register("notifyassignee@example.com");

        String teamJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String teamId = objectMapper.readTree(teamJson).get("data").get("id").asText();

        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"notifyassignee@example.com"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String inviteToken = objectMapper.readTree(inviteJson).get("data").get("token").asText();
        String acceptJson = mockMvc.perform(post("/api/invitations/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + assigneeToken))
                .andReturn().getResponse().getContentAsString();
        String assigneeId = objectMapper.readTree(acceptJson).get("data").get("userId").asText();

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Notify me","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskJson).get("data").get("id").asText();

        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + assigneeId + "\"}"))
                .andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + assigneeToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].type").value("TASK_ASSIGNED"))
                        .andExpect(jsonPath("$.data[0].read").value(false)));
    }
}
