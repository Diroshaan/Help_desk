package com.helpdesk.queue.dto;

import java.util.List;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Response for GET /api/queue/{ticketId}: the ticket itself (whose
 * createdAt/assignedAt/resolvedAt/updatedAt fields already form its
 * history timeline), its resolution if one has been posted yet, and every
 * staff note left on it. A composition of the other three response DTOs
 * rather than a new flat shape, so each stays reusable on its own (e.g.
 * TicketQueueResponse alone for the list endpoint).
 */
public class TicketQueueDetailResponse {

    private final TicketQueueResponse ticket;
    private final ResolutionResponse resolution;
    private final List<StaffNoteResponse> notes;

    public TicketQueueDetailResponse(TicketQueueResponse ticket, ResolutionResponse resolution,
                                      List<StaffNoteResponse> notes) {
        this.ticket = ticket;
        this.resolution = resolution;
        this.notes = notes;
    }

    public TicketQueueResponse getTicket() {
        return ticket;
    }
    public ResolutionResponse getResolution() {
        return resolution;
    }
    public List<StaffNoteResponse> getNotes() {
        return notes;
    }
}
