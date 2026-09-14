package com.helpdesk.queue.dto;

import com.helpdesk.queue.entity.StaffNote;

import java.time.LocalDateTime;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 */
public class StaffNoteResponse {

    private final Long id;
    private final Long ticketId;
    private final Long officerId;
    private final String note;
    private final LocalDateTime createdAt;

    public StaffNoteResponse(Long id, Long ticketId, Long officerId, String note, LocalDateTime createdAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.officerId = officerId;
        this.note = note;
        this.createdAt = createdAt;
    }

    public static StaffNoteResponse from(StaffNote staffNote) {
        return new StaffNoteResponse(staffNote.getId(), staffNote.getTicketId(), staffNote.getOfficerId(),
                staffNote.getNote(), staffNote.getCreatedAt());
    }

    public Long getId() {
        return id;
    }
    public Long getTicketId() {
        return ticketId;
    }
    public Long getOfficerId() {
        return officerId;
    }
    public String getNote() {
        return note;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
