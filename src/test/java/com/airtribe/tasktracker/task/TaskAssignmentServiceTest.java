package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.team.Team;
import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamMembershipService;
import com.airtribe.tasktracker.team.TeamRole;
import com.airtribe.tasktracker.team.TeamService;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TeamService teamService;
    @Mock private TeamMembershipService teamMembershipService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TaskService service() {
        return new TaskService(taskRepository, teamService, teamMembershipService, eventPublisher);
    }

    private Team sampleTeam(UUID id) {
        Team team = new Team();
        team.setId(id);
        return team;
    }

    private User sampleUser(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Task sampleTask(UUID teamId, UUID creatorId) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setTeam(sampleTeam(teamId));
        task.setCreatedBy(sampleUser(creatorId));
        task.setStatus(TaskStatus.OPEN);
        task.setPriority(TaskPriority.MEDIUM);
        return task;
    }

    @Test
    void changeStatusUpdatesAndSaves() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(membership);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service().changeStatus(task.getId(), creatorId, TaskStatus.IN_PROGRESS);

        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void assignTaskRequiresAssigneeToBeTeamMember() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID nonMemberId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership creatorMembership = new TeamMembership();
        creatorMembership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(creatorMembership);
        when(teamMembershipService.requireMember(teamId, nonMemberId))
                .thenThrow(new ForbiddenException("You are not a member of this team."));

        assertThatThrownBy(() -> service().assignTask(task.getId(), creatorId, nonMemberId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void assignTaskSetsAssigneeWhenValid() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership creatorMembership = new TeamMembership();
        creatorMembership.setRole(TeamRole.MEMBER);
        TeamMembership assigneeMembership = new TeamMembership();
        assigneeMembership.setUser(sampleUser(assigneeId));
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(creatorMembership);
        when(teamMembershipService.requireMember(teamId, assigneeId)).thenReturn(assigneeMembership);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service().assignTask(task.getId(), creatorId, assigneeId);

        assertThat(updated.getAssignee().getId()).isEqualTo(assigneeId);
    }

    @Test
    void listMyTasksFiltersByAssignee() {
        UUID userId = UUID.randomUUID();
        org.springframework.data.domain.Page<Task> emptyPage =
                org.springframework.data.domain.Page.empty();
        when(taskRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Task>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(emptyPage);

        org.springframework.data.domain.Page<Task> result = service().listMyTasks(
                userId, null, null, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result).isEqualTo(emptyPage);
    }
}
