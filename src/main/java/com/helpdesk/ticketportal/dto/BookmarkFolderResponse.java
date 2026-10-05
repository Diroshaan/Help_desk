package com.helpdesk.ticketportal.dto;

import com.helpdesk.ticketportal.entity.BookmarkFolder;

/**
 * Response body for a bookmark folder. bookmarkCount isn't a column; it is counted
 * from one list of the student's bookmarks.
 */
public class BookmarkFolderResponse {

    private final Long id;
    private final String name;
    private final String colour;
    private final long bookmarkCount;

    public BookmarkFolderResponse(Long id, String name, String colour, long bookmarkCount) {
        this.id = id;
        this.name = name;
        this.colour = colour;
        this.bookmarkCount = bookmarkCount;
    }

    public static BookmarkFolderResponse from(BookmarkFolder folder, long bookmarkCount) {
        return new BookmarkFolderResponse(folder.getId(), folder.getName(), folder.getColour(), bookmarkCount);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColour() {
        return colour;
    }

    public long getBookmarkCount() {
        return bookmarkCount;
    }
}
