package com.airtribe.tasktracker.task;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.common.web.PageMeta;
import com.airtribe.tasktracker.security.UserPrincipal;
import com.airtribe.tasktracker.task.dto.AssignTaskRequest;
import com.airtribe.tasktracker.task.dto.ChangeStatusRequest;
import com.airtribe.tasktracker.task.dto.CreateTaskRequest;
import com.airtribe.tasktracker.task.dto.TaskResponse;
import com.airtribe.tasktracker.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
public class TaskController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "dueDate", "title", "priority", "status");

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/teams/{teamId}/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> create(@AuthenticationPrincipal UserPrincipal principal,
                                                              @PathVariable UUID teamId,
                                                              @Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.createTask(teamId, principal.getUserId(), request.title(), request.description(),
                request.priority(), request.dueDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TaskResponse.from(task)));
    }

    @GetMapping("/api/teams/{teamId}/tasks")
    public ApiResponse<List<TaskResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID teamId,
                                                 @RequestParam(required = false) TaskStatus status,
                                                 @RequestParam(required = false) UUID assignee,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "createdAt,desc") String sort,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit, parseSort(sort));
        Page<Task> result = taskService.listTasks(teamId, principal.getUserId(), status, assignee, q, pageRequest);
        List<TaskResponse> data = result.getContent().stream().map(TaskResponse::from).toList();
        return ApiResponse.ok(data, new PageMeta(page, limit, result.getTotalElements()));
    }

    @GetMapping("/api/tasks/{taskId}")
    public ApiResponse<TaskResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID taskId) {
        return ApiResponse.ok(TaskResponse.from(taskService.getTaskForMember(taskId, principal.getUserId())));
    }

    @PutMapping("/api/tasks/{taskId}")
    public ApiResponse<TaskResponse> update(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID taskId,
                                             @Valid @RequestBody UpdateTaskRequest request) {
        Task task = taskService.updateTask(taskId, principal.getUserId(), request.title(), request.description(),
                request.priority(), request.dueDate());
        return ApiResponse.ok(TaskResponse.from(task));
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID taskId) {
        taskService.deleteTask(taskId, principal.getUserId());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/api/tasks/{taskId}/status")
    public ApiResponse<TaskResponse> changeStatus(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID taskId,
                                                    @Valid @RequestBody ChangeStatusRequest request) {
        Task task = taskService.changeStatus(taskId, principal.getUserId(), request.status());
        return ApiResponse.ok(TaskResponse.from(task));
    }

    @PatchMapping("/api/tasks/{taskId}/assign")
    public ApiResponse<TaskResponse> assign(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID taskId,
                                             @Valid @RequestBody AssignTaskRequest request) {
        Task task = taskService.assignTask(taskId, principal.getUserId(), request.assigneeId());
        return ApiResponse.ok(TaskResponse.from(task));
    }

    @GetMapping("/api/tasks/mine")
    public ApiResponse<List<TaskResponse>> mine(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestParam(required = false) TaskStatus status,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "createdAt,desc") String sort,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int limit) {
        PageRequest pageRequest = PageRequest.of(page, limit, parseSort(sort));
        Page<Task> result = taskService.listMyTasks(principal.getUserId(), status, q, pageRequest);
        List<TaskResponse> data = result.getContent().stream().map(TaskResponse::from).toList();
        return ApiResponse.ok(data, new PageMeta(page, limit, result.getTotalElements()));
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0];
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new BadRequestException("Cannot sort by '" + field + "'.");
        }
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
