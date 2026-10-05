package com.helpdesk.ticketportal.dto;

import java.time.LocalDateTime;

/**
 * The officer's answer to a ticket, as the owning student sees it.
 * Has no staff notes or officer id, so internal notes can't leak to students.
 * publishedAt is the resolution's createdAt.
 */
public class TicketResolutionResponse {

    private final Long id;
    private final String responseText;
    private final String officerName;
    private final String attachmentFileName;
    private final Long attachmentFileSize;
    private final LocalDateTime publishedAt;
    private final LocalDateTime updatedAt;

    public TicketResolutionResponse(Long id, String responseText, String officerName,
                                    String attachmentFileName, Long attachmentFileSize,
                                    LocalDateTime publishedAt, LocalDateTime updatedAt) {
        this.id = id;
        this.responseText = responseText;
        this.officerName = officerName;
        this.attachmentFileName = attachmentFileName;
        this.attachmentFileSize = attachmentFileSize;
        this.publishedAt = publishedAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getResponseText() {
        return responseText;
    }

    public String getOfficerName() {
        return officerName;
    }

    public String getAttachmentFileName() {
        return attachmentFileName;
    }

    public Long getAttachmentFileSize() {
        return attachmentFileSize;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
