package com.helpdesk.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One entry in a ticket's status history.
 * Weak entity with a composite key (ticketId, sequenceNo) - "the nth change of this ticket".
 * fromStatus is null only on the first row.
 */
@Entity
@Table(name = "ticket_status_changes")
@IdClass(TicketStatusChangeId.class)
public class TicketStatusChange {

    @Id
    @Column(name = "ticket_id")
    private Long ticketId;

    @Id
    @Column(name = "sequence_no")
    private Integer sequenceNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 20)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 20)
    private TicketStatus toStatus;

    // Student or officer who made the change; null if unknown.
    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    // Read-only link that gives a fresh schema the foreign key; ticketId is the column we write.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_status_changes_ticket"))
    private Ticket ticket;

    public TicketStatusChange() {}

    public TicketStatusChange(Long ticketId, Integer sequenceNo, TicketStatus fromStatus, TicketStatus toStatus,
                               Long changedByUserId, LocalDateTime changedAt) {
        this.ticketId = ticketId;
        this.sequenceNo = sequenceNo;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
    }

    public Long getTicketId() {
        return ticketId;
    }
    public Integer getSequenceNo() {
        return sequenceNo;
    }
    public TicketStatus getFromStatus() {
        return fromStatus;
    }
    public TicketStatus getToStatus() {
        return toStatus;
    }
    public Long getChangedByUserId() {
        return changedByUserId;
    }
    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
