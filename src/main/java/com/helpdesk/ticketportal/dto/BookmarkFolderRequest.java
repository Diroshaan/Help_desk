package com.helpdesk.ticketportal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating/renaming a bookmark folder (F3).
 *
 * Deliberately has only "name" and "colour" - no "id" and no "studentId".
 * Reusing the BookmarkFolder entity as the request body would let a client
 * send those two fields directly in the JSON payload; the service methods
 * ignore anything a caller sends for them (studentId always comes from the
 * logged-in session, id always comes from the URL path), so accepting them
 * here would be silently-ignored noise at best. Not declaring the fields at
 * all is the actual fix - there's nothing for a mass-assignment attempt to
 * bind onto.
 */
public class BookmarkFolderRequest {

    @NotBlank(message = "Folder name is required")
    @Size(max = 60, message = "Folder name must be 60 characters or fewer")
    private String name;

    // Optional (null = no colour, or "leave unchanged" on a rename), but if
    // sent it must be #RRGGBB. Checked here so a bad value is a clean 400 at
    // the door; the entity's own @Size(max = 7) only fires deep inside the save,
    // and never checked the format at all ("red" or "#zzzzzz" got through).
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
