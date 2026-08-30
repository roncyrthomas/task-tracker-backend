package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentControllerIT extends AbstractIntegrationTest {

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
                                {"title":"Attach spec","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(taskJson).get("data").get("id").asText();
    }

    @Test
    void uploadDownloadAndDeleteFlow() throws Exception {
        String token = register("uploader@example.com");
        String taskId = createTeamAndTask(token);
        MockMultipartFile file = new MockMultipartFile("file", "spec.txt", "text/plain", "hello world".getBytes());

        String uploadJson = mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.filename").value("spec.txt"))
                .andReturn().getResponse().getContentAsString();
        String attachmentId = objectMapper.readTree(uploadJson).get("data").get("id").asText();

        mockMvc.perform(get("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes("hello world".getBytes()));

        mockMvc.perform(delete("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/" + taskId + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadRejectsDisallowedContentTypeOverHttp() throws Exception {
        String token = register("uploader2@example.com");
        String taskId = createTeamAndTask(token);
        MockMultipartFile file = new MockMultipartFile("file", "app.exe", "application/x-msdownload", "x".getBytes());

        mockMvc.perform(multipart("/api/tasks/" + taskId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
