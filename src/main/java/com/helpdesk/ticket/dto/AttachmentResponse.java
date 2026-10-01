package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.entity.AttachmentKind;

import java.time.LocalDateTime;

public class AttachmentResponse {

    private final Long id;
    private final Long ticketId;
    private final String fileName;
    private final String fileType;
    private final Long fileSize;
    private final LocalDateTime uploadedAt;
    private final Long uploadedByUserId;
    private final String kind;

    // kind is AttachmentKind here, not String: AttachmentRepository's JPQL
    // constructor expression (commit 2) passes a.kind, which Hibernate sees
    // as the enum - a String parameter here would fail that @Query's
    // constructor match at startup. It is stored as its name() so the JSON
    // this DTO produces is still a plain string ("SUBMISSION").
    public AttachmentResponse(Long id, Long ticketId, String fileName, String fileType,
                              Long fileSize, LocalDateTime uploadedAt, Long uploadedByUserId,
                              AttachmentKind kind) {
        this.id = id;
        this.ticketId = ticketId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
        this.uploadedByUserId = uploadedByUserId;
        this.kind = kind == null ? null : kind.name();
    }

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getTicketId(), attachment.getFileName(),
                attachment.getFileType(), attachment.getFileSize(), attachment.getUploadedAt(),
                attachment.getUploadedByUserId(), attachment.getKind());
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
    public Long getFileSize() {
        return fileSize;
    }
    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }
    public String getKind() {
        return kind;
    }
}
