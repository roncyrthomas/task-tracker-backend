package com.airtribe.tasktracker.team;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ConflictException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private TeamService teamService;
    @Mock private TeamMembershipService teamMembershipService;
    @Mock private TeamMembershipRepository teamMembershipRepository;

    private InvitationService service() {
        return new InvitationService(invitationRepository, teamService, teamMembershipService, teamMembershipRepository);
    }

    private Team sampleTeam(UUID id) {
        Team team = new Team();
        team.setId(id);
        return team;
    }

    private User sampleUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        return user;
    }

    @Test
    void createInvitationRequiresAdminOrOwner() {
        UUID teamId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        when(teamMembershipService.requireRole(teamId, inviterId, Set.of(TeamRole.OWNER, TeamRole.ADMIN)))
                .thenThrow(new ForbiddenException("You do not have permission to perform this action."));

        assertThatThrownBy(() -> service().createInvitation(teamId, inviterId, "new@example.com"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createInvitationRejectsDuplicatePendingInvite() {
        UUID teamId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        when(invitationRepository.findByTeamIdAndEmailAndStatus(teamId, "new@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(new Invitation()));

        assertThatThrownBy(() -> service().createInvitation(teamId, inviterId, "new@example.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createInvitationSavesPendingInvitationWithToken() {
        UUID teamId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        when(invitationRepository.findByTeamIdAndEmailAndStatus(teamId, "new@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(teamService.getTeam(teamId)).thenReturn(sampleTeam(teamId));
        TeamMembership inviterMembership = new TeamMembership();
        inviterMembership.setUser(sampleUser("inviter@example.com"));
        when(teamMembershipService.requireMember(teamId, inviterId)).thenReturn(inviterMembership);
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));

        Invitation invitation = service().createInvitation(teamId, inviterId, "new@example.com");

        assertThat(invitation.getEmail()).isEqualTo("new@example.com");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.getToken()).isNotBlank();
        assertThat(invitation.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void acceptInvitationThrowsNotFoundForUnknownToken() {
        when(invitationRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().acceptInvitation("bad-token", sampleUser("a@b.com")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void acceptInvitationRejectsExpiredInvitation() {
        Invitation invitation = new Invitation();
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setEmail("a@b.com");
        invitation.setExpiresAt(Instant.now().minusSeconds(60));
        when(invitationRepository.findByToken("token")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().acceptInvitation("token", sampleUser("a@b.com")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptInvitationRejectsMismatchedEmail() {
        Invitation invitation = new Invitation();
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setEmail("intended@b.com");
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        when(invitationRepository.findByToken("token")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().acceptInvitation("token", sampleUser("someone-else@b.com")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptInvitationAddsMembershipAndMarksAccepted() {
        UUID teamId = UUID.randomUUID();
        Team team = sampleTeam(teamId);
        Invitation invitation = new Invitation();
        invitation.setTeam(team);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setEmail("a@b.com");
        invitation.setExpiresAt(Instant.now().plusSeconds(3600));
        when(invitationRepository.findByToken("token")).thenReturn(Optional.of(invitation));
        User user = sampleUser("a@b.com");
        when(teamMembershipRepository.existsByTeamIdAndUserId(teamId, user.getId())).thenReturn(false);
        TeamMembership membership = new TeamMembership();
        when(teamMembershipService.addMember(team, user, TeamRole.MEMBER)).thenReturn(membership);

        TeamMembership result = service().acceptInvitation("token", user);

        assertThat(result).isSameAs(membership);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(invitationRepository).save(invitation);
    }
}
