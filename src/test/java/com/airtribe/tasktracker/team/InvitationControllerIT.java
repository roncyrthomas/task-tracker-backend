package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvitationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email, String name) throws Exception {
        String body = """
                {"name":"%s","email":"%s","password":"supersecret1"}
                """.formatted(name, email);
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    private String createTeam(String ownerToken, String name) throws Exception {
        String body = """
                {"name":"%s","description":"desc"}
                """.formatted(name);
        String json = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("id").asText();
    }

    @Test
    void ownerInvitesAndInviteeAccepts() throws Exception {
        String ownerToken = register("owner3@example.com", "Owner Three");
        String teamId = createTeam(ownerToken, "Marketing");
        String inviteeToken = register("invitee@example.com", "Invitee");

        String inviteBody = """
                {"email":"invitee@example.com"}
                """;
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(inviteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(inviteJson).get("data").get("token").asText();

        mockMvc.perform(post("/api/invitations/" + token + "/accept")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));

        mockMvc.perform(get("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void nonAdminCannotInvite() throws Exception {
        String ownerToken = register("owner4@example.com", "Owner Four");
        String teamId = createTeam(ownerToken, "Sales");
        String memberToken = register("member@example.com", "Member");

        String inviteMemberBody = """
                {"email":"member@example.com"}
                """;
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(inviteMemberBody))
                .andReturn().getResponse().getContentAsString();
        String memberInviteToken = objectMapper.readTree(inviteJson).get("data").get("token").asText();
        mockMvc.perform(post("/api/invitations/" + memberInviteToken + "/accept")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        String outsiderInviteBody = """
                {"email":"someone@example.com"}
                """;
        mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON).content(outsiderInviteBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptingWithWrongEmailIsForbidden() throws Exception {
        String ownerToken = register("owner5@example.com", "Owner Five");
        String teamId = createTeam(ownerToken, "Support");
        String wrongUserToken = register("wrong-person@example.com", "Wrong Person");

        String inviteBody = """
                {"email":"intended@example.com"}
                """;
        String inviteJson = mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(inviteBody))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(inviteJson).get("data").get("token").asText();

        mockMvc.perform(post("/api/invitations/" + token + "/accept")
                        .header("Authorization", "Bearer " + wrongUserToken))
                .andExpect(status().isForbidden());
    }
}
