package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.Attachment;

import java.time.LocalDateTime;

public class AttachmentResponse {

    private final Long id;
    private final Long ticketId;
    private final String fileName;
    private final String fileType;
    private final String fileUrl;
    private final Long fileSize;
    private final LocalDateTime uploadedAt;

    public AttachmentResponse(Long id, Long ticketId, String fileName, String fileType,
                               String fileUrl, Long fileSize, LocalDateTime uploadedAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileUrl = fileUrl;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
    }

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getTicketId(), attachment.getFileName(),
                attachment.getFileType(), attachment.getFileUrl(), attachment.getFileSize(), attachment.getUploadedAt());
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
