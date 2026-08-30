package com.airtribe.tasktracker.attachment;

import com.airtribe.tasktracker.attachment.dto.AttachmentResponse;
import com.airtribe.tasktracker.common.web.ApiResponse;
import com.airtribe.tasktracker.security.UserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentResponse>> upload(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID taskId,
                                                                    @RequestParam("file") MultipartFile file) {
        Attachment attachment = attachmentService.upload(taskId, principal.getUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(AttachmentResponse.from(attachment)));
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID taskId, @PathVariable UUID attachmentId) {
        AttachmentService.AttachmentDownload download =
                attachmentService.download(taskId, attachmentId, principal.getUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.attachment().getFilename() + "\"")
                .contentType(MediaType.parseMediaType(download.attachment().getContentType()))
                .body(download.resource());
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID taskId, @PathVariable UUID attachmentId) {
        attachmentService.delete(taskId, attachmentId, principal.getUserId());
        return ApiResponse.ok(null);
    }
}
