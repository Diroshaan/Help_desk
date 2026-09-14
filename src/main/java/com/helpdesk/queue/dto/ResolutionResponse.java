package com.helpdesk.queue.dto;

import com.helpdesk.queue.entity.Resolution;

import java.time.LocalDateTime;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Attachment binary data is deliberately excluded here, same reasoning as
 * ticket.dto.AttachmentResponse - the file itself is downloaded separately,
 * not embedded in this JSON payload.
 */
public class ResolutionResponse {

    private final Long id;
    private final Long ticketId;
    private final Long officerId;
    private final String responseText;
    private final String attachmentFileName;
    private final String attachmentFileType;
    private final Long attachmentFileSize;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ResolutionResponse(Long id, Long ticketId, Long officerId, String responseText,
                               String attachmentFileName, String attachmentFileType, Long attachmentFileSize,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.officerId = officerId;
        this.responseText = responseText;
        this.attachmentFileName = attachmentFileName;
        this.attachmentFileType = attachmentFileType;
        this.attachmentFileSize = attachmentFileSize;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ResolutionResponse from(Resolution resolution) {
        return new ResolutionResponse(resolution.getId(), resolution.getTicketId(), resolution.getOfficerId(),
                resolution.getResponseText(), resolution.getAttachmentFileName(),
                resolution.getAttachmentFileType(), resolution.getAttachmentFileSize(),
                resolution.getCreatedAt(), resolution.getUpdatedAt());
    }

    public Long getId() {
        return id;
    }
    public Long getTicketId() {
        return ticketId;
    }
    public Long getOfficerId() {
        return officerId;
    }
    public String getResponseText() {
        return responseText;
    }
    public String getAttachmentFileName() {
        return attachmentFileName;
    }
    public String getAttachmentFileType() {
        return attachmentFileType;
    }
    public Long getAttachmentFileSize() {
        return attachmentFileSize;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
