package com.helpdesk.ticketportal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or renaming a bookmark folder.
 * Only name and colour - no id or studentId, so a client can't mass-assign them.
 */
public class BookmarkFolderRequest {

    @NotBlank(message = "Folder name is required")
    @Size(max = 60, message = "Folder name must be 60 characters or fewer")
    private String name;

    // Optional (null = no colour, or unchanged on rename), but if sent it must be #RRGGBB.
    @Size(max = 7)
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour must be a hex value like #1A2B3C")
    private String colour;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }
}
