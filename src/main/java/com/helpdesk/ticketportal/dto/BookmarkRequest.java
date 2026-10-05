package com.helpdesk.ticketportal.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for creating a bookmark. Only ticketId and folderId - no id or studentId. */
public class BookmarkRequest {

    @NotNull(message = "Ticket ID is required")
    private Long ticketId;

    // null means not filed in a folder
    private Long folderId;

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }
}
