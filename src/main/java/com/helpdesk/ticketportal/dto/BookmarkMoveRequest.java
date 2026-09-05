package com.helpdesk.ticketportal.dto;

/**
 * Request body for moving a bookmark into a different folder .
 *
 * folderId deliberately has no @NotNull - null is a valid, meaningful value
 * here: it unfiles the bookmark rather than assigning it to a folder.
 */
public class BookmarkMoveRequest {

    private Long folderId;

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }
}
