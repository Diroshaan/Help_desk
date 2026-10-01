package com.helpdesk.ticket.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * F2 - composite primary key for TicketStatusChange (#45).
 *
 * A weak entity's identity is its parent's key plus its own partial key, so
 * the @IdClass is exactly that pair - see TicketStatusChange's own Javadoc
 * for why there is no separate auto-increment id.
 */
public class TicketStatusChangeId implements Serializable {

    private Long ticketId;
    private Integer sequenceNo;

    public TicketStatusChangeId() {}    // Required no-argument constructor for JPA

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
