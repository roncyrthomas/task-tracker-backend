package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ConflictException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.user.User;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
public class InvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int INVITATION_TTL_DAYS = 7;

    private final InvitationRepository invitationRepository;
    private final TeamService teamService;
    private final TeamMembershipService teamMembershipService;
    private final TeamMembershipRepository teamMembershipRepository;

    public InvitationService(InvitationRepository invitationRepository, TeamService teamService,
                              TeamMembershipService teamMembershipService,
                              TeamMembershipRepository teamMembershipRepository) {
        this.invitationRepository = invitationRepository;
        this.teamService = teamService;
        this.teamMembershipService = teamMembershipService;
        this.teamMembershipRepository = teamMembershipRepository;
    }

    public Invitation createInvitation(UUID teamId, UUID inviterId, String email) {
        teamMembershipService.requireRole(teamId, inviterId, Set.of(TeamRole.OWNER, TeamRole.ADMIN));

        invitationRepository.findByTeamIdAndEmailAndStatus(teamId, email, InvitationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new ConflictException("There is already a pending invitation for " + email + ".");
                });

        Team team = teamService.getTeam(teamId);
        User inviter = teamMembershipService.requireMember(teamId, inviterId).getUser();

        Invitation invitation = new Invitation();
        invitation.setTeam(team);
        invitation.setEmail(email);
        invitation.setToken(randomToken());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedBy(inviter);
        invitation.setExpiresAt(Instant.now().plus(INVITATION_TTL_DAYS, ChronoUnit.DAYS));
        return invitationRepository.save(invitation);
    }

    public TeamMembership acceptInvitation(String token, User acceptingUser) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Invitation not found."));

        if (invitation.getStatus() != InvitationStatus.PENDING || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This invitation is no longer valid.");
        }
        if (!invitation.getEmail().equalsIgnoreCase(acceptingUser.getEmail())) {
            throw new ForbiddenException("This invitation was sent to a different email address.");
        }

        UUID teamId = invitation.getTeam().getId();
        if (teamMembershipRepository.existsByTeamIdAndUserId(teamId, acceptingUser.getId())) {
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitationRepository.save(invitation);
            return teamMembershipRepository.findByTeamIdAndUserId(teamId, acceptingUser.getId()).orElseThrow();
        }

        TeamMembership membership = teamMembershipService.addMember(invitation.getTeam(), acceptingUser, TeamRole.MEMBER);
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
        return membership;
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
