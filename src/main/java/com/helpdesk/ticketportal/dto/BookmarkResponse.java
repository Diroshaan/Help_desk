package com.helpdesk.ticketportal.dto;

import com.helpdesk.ticketportal.entity.Bookmark;

import java.time.LocalDateTime;

public class BookmarkResponse {

    private final Long id;
    private final Long ticketId;
    private final Long folderId;
    private final LocalDateTime createdAt;

    public BookmarkResponse(Long id, Long ticketId, Long folderId, LocalDateTime createdAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.folderId = folderId;
        this.createdAt = createdAt;
    }

    public static BookmarkResponse from(Bookmark bookmark) {
        return new BookmarkResponse(bookmark.getId(), bookmark.getTicketId(), bookmark.getFolderId(), bookmark.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Long getFolderId() {
        return folderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
