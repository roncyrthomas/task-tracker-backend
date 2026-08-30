package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.common.exception.BadRequestException;
import com.airtribe.tasktracker.common.exception.ForbiddenException;
import com.airtribe.tasktracker.task.Task;
import com.airtribe.tasktracker.task.TaskService;
import com.airtribe.tasktracker.team.Team;
import com.airtribe.tasktracker.team.TeamMembership;
import com.airtribe.tasktracker.team.TeamMembershipService;
import com.airtribe.tasktracker.team.TeamRole;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private TaskService taskService;
    @Mock private TeamMembershipService teamMembershipService;
    @Mock private UserService userService;
    @Mock private StorageService storageService;

    private AttachmentService service() {
        return new AttachmentService(attachmentRepository, taskService, teamMembershipService, userService, storageService);
    }

    private Task sampleTask(UUID teamId) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        Team team = new Team();
        team.setId(teamId);
        task.setTeam(team);
        return task;
    }

    @Test
    void uploadRejectsFileOverSizeLimit() {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        when(taskService.getTaskForMember(task.getId(), uploaderId)).thenReturn(task);
        byte[] tooBig = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooBig);

        assertThatThrownBy(() -> service().upload(task.getId(), uploaderId, file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadRejectsDisallowedContentType() {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        when(taskService.getTaskForMember(task.getId(), uploaderId)).thenReturn(task);
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/x-msdownload", "x".getBytes());

        assertThatThrownBy(() -> service().upload(task.getId(), uploaderId, file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadSavesAttachmentWhenValid() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        User uploader = new User();
        uploader.setId(uploaderId);
        when(taskService.getTaskForMember(task.getId(), uploaderId)).thenReturn(task);
        when(userService.findById(uploaderId)).thenReturn(uploader);
        when(storageService.store(eq(task.getId()), any(), any(), anyLong())).thenReturn("path/to/file.txt");
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hi".getBytes());

        Attachment attachment = service().upload(task.getId(), uploaderId, file);

        assertThat(attachment.getStoragePath()).isEqualTo("path/to/file.txt");
        assertThat(attachment.getUploadedBy()).isSameAs(uploader);
    }

    @Test
    void deleteForbiddenForNonUploaderNonAdmin() {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        Attachment attachment = new Attachment();
        attachment.setId(UUID.randomUUID());
        User uploader = new User();
        uploader.setId(uploaderId);
        attachment.setUploadedBy(uploader);
        when(taskService.getTaskForMember(task.getId(), otherUserId)).thenReturn(task);
        when(attachmentRepository.findByIdAndTaskId(attachment.getId(), task.getId())).thenReturn(Optional.of(attachment));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, otherUserId)).thenReturn(membership);

        assertThatThrownBy(() -> service().delete(task.getId(), attachment.getId(), otherUserId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteAllowedForUploader() {
        UUID teamId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        Task task = sampleTask(teamId);
        Attachment attachment = new Attachment();
        attachment.setId(UUID.randomUUID());
        attachment.setStoragePath("some/path.txt");
        User uploader = new User();
        uploader.setId(uploaderId);
        attachment.setUploadedBy(uploader);
        when(taskService.getTaskForMember(task.getId(), uploaderId)).thenReturn(task);
        when(attachmentRepository.findByIdAndTaskId(attachment.getId(), task.getId())).thenReturn(Optional.of(attachment));
        TeamMembership membership = new TeamMembership();
        membership.setRole(TeamRole.MEMBER);
        when(teamMembershipService.requireMember(teamId, uploaderId)).thenReturn(membership);

        service().delete(task.getId(), attachment.getId(), uploaderId);

        verify(storageService).delete("some/path.txt");
        verify(attachmentRepository).delete(attachment);
    }

    private static UUID eq(UUID id) {
        return org.mockito.ArgumentMatchers.eq(id);
    }
}
