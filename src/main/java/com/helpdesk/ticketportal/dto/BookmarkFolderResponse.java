package com.helpdesk.ticketportal.dto;

import com.helpdesk.ticketportal.entity.BookmarkFolder;

/**
 * Response body for a bookmark folder (F3): { id, name, colour, bookmarkCount }.
 *
 * bookmarkCount is not a column on BookmarkFolder - it's computed from the
 * Bookmark table. Callers should populate it from a single grouped count
 * query (see BookmarkRepository.countByFolderIdForStudent) rather than one
 * countBy... query per folder, which would be an N+1 query per page load.
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
