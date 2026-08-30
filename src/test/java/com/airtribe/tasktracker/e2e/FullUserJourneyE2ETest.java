package com.airtribe.tasktracker.e2e;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FullUserJourneyE2ETest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String field(String json, String... path) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        for (String p : path) {
            node = node.get(p);
        }
        return node.asText();
    }

    @Test
    void registerCreateTeamInviteAssignCommentAttachAndNotifyEndToEnd() throws Exception {
        String leadJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Lee","email":"lead@example.com","password":"supersecret1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String leadToken = field(leadJson, "data", "accessToken");

        String memberJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mo Member","email":"member@example.com","password":"supersecret1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String memberToken = field(memberJson, "data", "accessToken");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Leeman","avatarUrl":"https://img/lead.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Lead Leeman"));

        String teamJson = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Launch Squad","description":"Ships the launch"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String teamId = field(teamJson, "data", "id");

        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"member@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String inviteToken = field(inviteJson, "data", "token");

        String acceptJson = mockMvc.perform(post("/api/invitations/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andReturn().getResponse().getContentAsString();
        String memberId = field(acceptJson, "data", "userId");

        mockMvc.perform(get("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        String aiJson = mockMvc.perform(post("/api/tasks/ai/generate-description")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Prepare launch checklist","notes":"covers infra, comms, and support"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String aiDescription = field(aiJson, "data", "description");
        assertThat(aiDescription).isNotBlank();

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Object() {
                            public final String title = "Prepare launch checklist";
                            public final String description = aiDescription;
                            public final String priority = "HIGH";
                        })))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String taskId = field(taskJson, "data", "id");

        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + memberId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(memberId));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + memberToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data[0].type").value("TASK_ASSIGNED")));

        String notificationsJson = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + memberToken))
                .andReturn().getResponse().getContentAsString();
        String notificationId = objectMapper.readTree(notificationsJson).get("data").get(0).get("id").asText();
        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        mockMvc.perform(get("/api/tasks/mine").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Infra checklist is drafted, see attached."}
                                """))
                .andExpect(status().isCreated());

        MockMultipartFile checklist = new MockMultipartFile(
                "file", "checklist.txt", "text/plain", "1. DNS\n2. Monitoring\n3. Rollback plan".getBytes());
        String attachmentJson = mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(checklist)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attachmentId = field(attachmentJson, "data", "id");

        mockMvc.perform(get("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes("1. DNS\n2. Monitoring\n3. Rollback plan".getBytes()));

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/teams/" + teamId + "/tasks?status=COMPLETED")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/teams/" + teamId + "/tasks?q=checklist")
                        .header("Authorization", "Bearer " + leadToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(taskId));

        mockMvc.perform(delete("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        String leadRefreshToken = field(leadJson, "data", "refreshToken");
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + leadRefreshToken + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + leadRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
