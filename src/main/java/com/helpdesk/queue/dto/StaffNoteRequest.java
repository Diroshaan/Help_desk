package com.helpdesk.queue.dto;

import jakarta.validation.constraints.NotBlank;

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
