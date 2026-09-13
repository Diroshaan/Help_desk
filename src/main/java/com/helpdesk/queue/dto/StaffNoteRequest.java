package com.helpdesk.queue.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Request body for POST /api/queue/{id}/notes.
 */
public class StaffNoteRequest {

    @NotBlank(message = "Note text is required")
    private String note;

    public String getNote() {
        return note;
    }
    public void setNote(String note) {
        this.note = note;
    }
}
