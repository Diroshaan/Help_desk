package com.helpdesk.ticket.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for TicketStatusChange: the ticket's id plus the partial key sequenceNo. */
public class TicketStatusChangeId implements Serializable {

    private Long ticketId;
    private Integer sequenceNo;

    public TicketStatusChangeId() {}

    public TicketStatusChangeId(Long ticketId, Integer sequenceNo) {
        this.ticketId = ticketId;
        this.sequenceNo = sequenceNo;
    }

    public Long getTicketId() {
        return ticketId;
    }
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    public Integer getSequenceNo() {
        return sequenceNo;
    }
    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TicketStatusChangeId)) {
            return false;
        }
        TicketStatusChangeId that = (TicketStatusChangeId) o;
        return Objects.equals(ticketId, that.ticketId) && Objects.equals(sequenceNo, that.sequenceNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketId, sequenceNo);
    }
}
