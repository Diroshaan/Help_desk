package com.helpdesk.queue.dto;

import com.helpdesk.ticket.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body for a status change. Only OPEN -> IN_PROGRESS is allowed here;
 * RESOLVED goes through the resolution endpoint.
 */
public class TicketStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private TicketStatus status;

    public TicketStatus getStatus() {
        return status;
    }
    public void setStatus(TicketStatus status) {
        this.status = status;
    }
}
