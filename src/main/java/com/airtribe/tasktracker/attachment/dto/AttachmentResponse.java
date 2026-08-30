package com.airtribe.tasktracker.attachment.dto;

import com.airtribe.tasktracker.attachment.Attachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(UUID id, UUID taskId, UUID uploadedBy, String filename,
                                  String contentType, long sizeBytes, Instant createdAt) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(), attachment.getTask().getId(), attachment.getUploadedBy().getId(),
                attachment.getFilename(), attachment.getContentType(), attachment.getSizeBytes(),
                attachment.getCreatedAt());
    }
}
