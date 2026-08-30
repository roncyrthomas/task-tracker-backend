package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskService;
import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamMembershipService;
import com.airtribe.tasktracker.team.TeamRole;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    public record AttachmentDownload(Attachment attachment, Resource resource) {
    }

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "application/pdf", "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip");

    private final AttachmentRepository attachmentRepository;
    private final TaskService taskService;
    private final TeamMembershipService teamMembershipService;
    private final UserService userService;
    private final StorageService storageService;

    public AttachmentService(AttachmentRepository attachmentRepository, TaskService taskService,
                              TeamMembershipService teamMembershipService, UserService userService,
                              StorageService storageService) {
        this.attachmentRepository = attachmentRepository;
        this.taskService = taskService;
        this.teamMembershipService = teamMembershipService;
        this.userService = userService;
        this.storageService = storageService;
    }

    public Attachment upload(UUID taskId, UUID uploaderId, MultipartFile file) {
        Task task = taskService.getTaskForMember(taskId, uploaderId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attachment file is required.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("Attachment exceeds the 10MB size limit.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Attachment content type '" + contentType + "' is not allowed.");
        }

        User uploader = userService.findById(uploaderId);
        String storagePath;
        try {
            storagePath = storageService.store(taskId, file.getOriginalFilename(), file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new BadRequestException("Unable to store attachment.");
        }

        Attachment attachment = new Attachment();
        attachment.setTask(task);
        attachment.setUploadedBy(uploader);
        attachment.setFilename(safeDisplayName(file.getOriginalFilename()));
        attachment.setStoragePath(storagePath);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        return attachmentRepository.save(attachment);
    }

    public AttachmentDownload download(UUID taskId, UUID attachmentId, UUID requesterId) {
        taskService.getTaskForMember(taskId, requesterId);
        Attachment attachment = findAttachment(taskId, attachmentId);
        return new AttachmentDownload(attachment, storageService.load(attachment.getStoragePath()));
    }

    public void delete(UUID taskId, UUID attachmentId, UUID requesterId) {
        Task task = taskService.getTaskForMember(taskId, requesterId);
        Attachment attachment = findAttachment(taskId, attachmentId);
        TeamMembership membership = teamMembershipService.requireMember(task.getTeam().getId(), requesterId);
        boolean isUploader = attachment.getUploadedBy().getId().equals(requesterId);
        boolean isTeamAdmin = membership.getRole() == TeamRole.OWNER || membership.getRole() == TeamRole.ADMIN;
        if (!isUploader && !isTeamAdmin) {
            throw new ForbiddenException("You do not have permission to delete this attachment.");
        }
        storageService.delete(attachment.getStoragePath());
        attachmentRepository.delete(attachment);
    }

    private Attachment findAttachment(UUID taskId, UUID attachmentId) {
        return attachmentRepository.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new NotFoundException("Attachment not found."));
    }

    private String safeDisplayName(String original) {
        String name = original == null ? "file" : Path.of(original).getFileName().toString();
        return name.isBlank() ? "file" : name;
    }
}
