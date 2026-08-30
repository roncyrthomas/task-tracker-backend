package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import com.airtribe.tasktracker.team.dto.CreateTeamRequest;
import com.airtribe.tasktracker.team.dto.TeamMemberResponse;
import com.airtribe.tasktracker.team.dto.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final TeamMembershipService teamMembershipService;

    public TeamController(TeamService teamService, TeamMembershipService teamMembershipService) {
        this.teamService = teamService;
        this.teamMembershipService = teamMembershipService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                              @Valid @RequestBody CreateTeamRequest request) {
        Team team = teamService.createTeam(principal.getUserId(), request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TeamResponse.from(team)));
    }

    @GetMapping
    public ApiResponse<List<TeamResponse>> myTeams(@AuthenticationPrincipal UserPrincipal principal) {
        List<TeamResponse> teams = teamService.listMyTeams(principal.getUserId()).stream()
                .map(TeamResponse::from).toList();
        return ApiResponse.ok(teams);
    }

    @GetMapping("/{teamId}")
    public ApiResponse<TeamResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID teamId) {
        teamMembershipService.requireMember(teamId, principal.getUserId());
        return ApiResponse.ok(TeamResponse.from(teamService.getTeam(teamId)));
    }

    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamMemberResponse>> members(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID teamId) {
        teamMembershipService.requireMember(teamId, principal.getUserId());
        List<TeamMemberResponse> members = teamService.listMembers(teamId).stream()
                .map(TeamMemberResponse::from).toList();
        return ApiResponse.ok(members);
    }
}
