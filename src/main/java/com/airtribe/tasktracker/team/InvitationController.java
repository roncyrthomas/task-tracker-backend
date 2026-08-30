package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import com.airtribe.tasktracker.team.dto.CreateInvitationRequest;
import com.airtribe.tasktracker.team.dto.InvitationResponse;
import com.airtribe.tasktracker.team.dto.TeamMemberResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/teams/{teamId}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> invite(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID teamId,
                                                                    @Valid @RequestBody CreateInvitationRequest request) {
        Invitation invitation = invitationService.createInvitation(teamId, principal.getUserId(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(InvitationResponse.from(invitation)));
    }

    @PostMapping("/api/invitations/{token}/accept")
    public ApiResponse<TeamMemberResponse> accept(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable String token) {
        TeamMembership membership = invitationService.acceptInvitation(token, principal.getUser());
        return ApiResponse.ok(TeamMemberResponse.from(membership));
    }
}
