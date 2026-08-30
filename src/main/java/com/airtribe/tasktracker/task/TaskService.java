package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.notification.TaskAssignedEvent;
import com.airtribe.tasktracker.notification.TaskUpdatedEvent;
import com.airtribe.tasktracker.team.Team;
import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamMembershipService;
import com.airtribe.tasktracker.team.TeamRole;
import com.airtribe.tasktracker.team.TeamService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TeamService teamService;
    private final TeamMembershipService teamMembershipService;
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository, TeamService teamService,
                        TeamMembershipService teamMembershipService, ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.teamService = teamService;
        this.teamMembershipService = teamMembershipService;
        this.eventPublisher = eventPublisher;
    }

    public Task createTask(UUID teamId, UUID creatorId, String title, String description,
                            TaskPriority priority, Instant dueDate) {
        TeamMembership membership = teamMembershipService.requireMember(teamId, creatorId);
        Team team = teamService.getTeam(teamId);

        Task task = new Task();
        task.setTeam(team);
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(TaskStatus.OPEN);
        task.setPriority(priority == null ? TaskPriority.MEDIUM : priority);
        task.setDueDate(dueDate);
        task.setCreatedBy(membership.getUser());
        return taskRepository.save(task);
    }

    public Task getTaskForMember(UUID taskId, UUID requesterId) {
        Task task = findById(taskId);
        teamMembershipService.requireMember(task.getTeam().getId(), requesterId);
        return task;
    }

    public Task updateTask(UUID taskId, UUID actingUserId, String title, String description,
                            TaskPriority priority, Instant dueDate) {
        Task task = findById(taskId);
        requireCanEdit(task, actingUserId);
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority == null ? task.getPriority() : priority);
        task.setDueDate(dueDate);
        Task saved = taskRepository.save(task);
        publishUpdateIfAssigneeDiffers(saved, actingUserId);
        return saved;
    }

    public void deleteTask(UUID taskId, UUID actingUserId) {
        Task task = findById(taskId);
        requireCanEdit(task, actingUserId);
        taskRepository.delete(task);
    }

    public Page<Task> listTasks(UUID teamId, UUID requesterId, TaskStatus status, UUID assigneeId,
                                 String search, Pageable pageable) {
        teamMembershipService.requireMember(teamId, requesterId);
        Specification<Task> spec = Specification.where(TaskSpecifications.belongsToTeam(teamId))
                .and(TaskSpecifications.hasStatus(status))
                .and(TaskSpecifications.hasAssignee(assigneeId))
                .and(TaskSpecifications.titleOrDescriptionContains(search));
        return taskRepository.findAll(spec, pageable);
    }

    public Task changeStatus(UUID taskId, UUID actingUserId, TaskStatus newStatus) {
        Task task = findById(taskId);
        requireCanEdit(task, actingUserId);
        task.setStatus(newStatus);
        Task saved = taskRepository.save(task);
        publishUpdateIfAssigneeDiffers(saved, actingUserId);
        return saved;
    }

    public Task assignTask(UUID taskId, UUID actingUserId, UUID assigneeId) {
        Task task = findById(taskId);
        requireCanEdit(task, actingUserId);
        TeamMembership assigneeMembership;
        try {
            assigneeMembership = teamMembershipService.requireMember(task.getTeam().getId(), assigneeId);
        } catch (ForbiddenException ex) {
            throw new BadRequestException("The assignee must be a member of this team.");
        }
        task.setAssignee(assigneeMembership.getUser());
        Task saved = taskRepository.save(task);
        if (!saved.getAssignee().getId().equals(actingUserId)) {
            eventPublisher.publishEvent(new TaskAssignedEvent(
                    saved.getId(), saved.getTitle(), saved.getTeam().getId(), saved.getAssignee().getId()));
        }
        return saved;
    }

    public Page<Task> listMyTasks(UUID userId, TaskStatus status, String search, Pageable pageable) {
        Specification<Task> spec = Specification.where(TaskSpecifications.assignedToUser(userId))
                .and(TaskSpecifications.hasStatus(status))
                .and(TaskSpecifications.titleOrDescriptionContains(search));
        return taskRepository.findAll(spec, pageable);
    }

    Task findById(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found."));
    }

    void requireCanEdit(Task task, UUID userId) {
        TeamMembership membership = teamMembershipService.requireMember(task.getTeam().getId(), userId);
        boolean isCreator = task.getCreatedBy().getId().equals(userId);
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(userId);
        boolean isTeamAdmin = membership.getRole() == TeamRole.OWNER || membership.getRole() == TeamRole.ADMIN;
        if (!isCreator && !isAssignee && !isTeamAdmin) {
            throw new ForbiddenException("You do not have permission to modify this task.");
        }
    }

    private void publishUpdateIfAssigneeDiffers(Task task, UUID actingUserId) {
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(actingUserId)) {
            eventPublisher.publishEvent(new TaskUpdatedEvent(
                    task.getId(), task.getTitle(), task.getTeam().getId(), task.getAssignee().getId()));
        }
    }
}
