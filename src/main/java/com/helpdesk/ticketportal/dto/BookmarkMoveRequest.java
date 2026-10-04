package com.helpdesk.ticketportal.dto;

/** Request body for moving a bookmark. A null folderId unfiles it, so there is no @NotNull. */
public class BookmarkMoveRequest {

    private Long folderId;

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }
}
