package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.team.*;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

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

    private Task sampleTask(UUID teamId, UUID creatorId, UUID assigneeId) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setTeam(sampleTeam(teamId));
        task.setCreatedBy(sampleUser(creatorId));
        task.setStatus(TaskStatus.OPEN);
        task.setPriority(TaskPriority.MEDIUM);
        if (assigneeId != null) {
            task.setAssignee(sampleUser(assigneeId));
        }
        return task;
    }

    @Test
    void createTaskRequiresTeamMembership() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        when(teamMembershipService.requireMember(eq(teamId), eq(creatorId)))
                .thenThrow(new ForbiddenException("You are not a member of this team."));

        assertThatThrownBy(() -> service().createTask(teamId, creatorId, "Title", "Desc", TaskPriority.HIGH, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createTaskDefaultsPriorityToMediumWhenNull() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        TeamMembership membership = new TeamMembership();
        membership.setUser(sampleUser(creatorId));
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(membership);
        when(teamService.getTeam(teamId)).thenReturn(sampleTeam(teamId));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task task = service().createTask(teamId, creatorId, "Title", "Desc", null, null);

        assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void getTaskForMemberThrowsNotFoundWhenMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getTaskForMember(taskId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateTaskAllowedForCreator() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId, null);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(membership);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service().updateTask(task.getId(), creatorId, "New Title", "New Desc", TaskPriority.LOW, null);

        assertThat(updated.getTitle()).isEqualTo("New Title");
    }

    @Test
    void updateTaskForbiddenForUnrelatedMember() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID unrelatedUserId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId, null);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, unrelatedUserId)).thenReturn(membership);

        assertThatThrownBy(() -> service().updateTask(task.getId(), unrelatedUserId, "X", "Y", TaskPriority.LOW, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateTaskAllowedForTeamAdmin() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId, null);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.ADMIN);
        when(teamMembershipService.requireMember(teamId, adminId)).thenReturn(membership);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service().updateTask(task.getId(), adminId, "Admin Edit", "Y", TaskPriority.LOW, null);

        assertThat(updated.getTitle()).isEqualTo("Admin Edit");
    }

    @Test
    void deleteTaskDelegatesToRepositoryWhenAllowed() {
        UUID teamId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Task task = sampleTask(teamId, creatorId, null);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, creatorId)).thenReturn(membership);

        service().deleteTask(task.getId(), creatorId);

        verify(taskRepository).delete(task);
    }
}
