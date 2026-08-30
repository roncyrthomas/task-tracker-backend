package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"name":"User","email":"%s","password":"supersecret1"}
                """.formatted(email);
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
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

    @Test
    void memberCanCreateListFilterAndSearchTasks() throws Exception {
        String token = register("taskowner1@example.com");
        String teamId = createTeam(token);

        mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write proposal","description":"Draft the Q3 proposal","priority":"HIGH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Fix bug","description":"Null pointer in checkout"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2));

        mockMvc.perform(get("/api/teams/" + teamId + "/tasks?q=proposal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Write proposal"));
    }

    @Test
    void onlyCreatorAssigneeOrAdminCanEditTask() throws Exception {
        String ownerToken = register("taskowner2@example.com");
        String teamId = createTeam(ownerToken);
        String outsiderToken = register("taskoutsider@example.com");

        String taskJson = mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Original","description":"d"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(taskJson).get("data").get("id").asText();

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","description":"d"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated by creator","description":"d"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated by creator"));
    }
}
