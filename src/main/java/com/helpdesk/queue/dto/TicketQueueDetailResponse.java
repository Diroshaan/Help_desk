package com.helpdesk.queue.dto;

import java.util.List;

/** One queued ticket with its resolution (null if none yet) and staff notes. */
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
