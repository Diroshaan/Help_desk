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
 * F2 - one entry in a ticket's status-change history (#45).
 *
 * A WEAK ENTITY: it cannot exist without its ticket, and its identity is the
 * pair (ticketId, sequenceNo) - see TicketStatusChangeId. sequenceNo is a
 * PARTIAL KEY, unique only within one ticket ("the 3rd change of ticket
 * 12"), and the relationship to Ticket is IDENTIFYING, because the ticket's
 * own primary key is part of this row's key. An auto-increment id was
 * deliberately not used: it would make a history row identifiable on its
 * own, independent of its ticket, and lose the natural "nth change of THIS
 * ticket" key the requirement asks for.
 *
 * fromStatus is null only for a ticket's very first row (OPEN has no "from").
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

    // @JdbcTypeCode(VARCHAR): the same ENUM-column trap as Ticket.status -
    // see that field's comment.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 20)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 20)
    private TicketStatus toStatus;

    // The student (create, withdraw) or the officer who acted; null means
    // unknown (F4 has not yet started passing its officer id - contract C2).
    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    // Read-only: ticketId above is the writable column. This association
    // exists only so a FRESH schema gets the foreign key straight from the
    // entity; on the shared (Aiven) database the same key is added by the
    // state-aware migration script instead (docs/migrations/2026-10-02),
    // the same split used for every other ticket-side key (see
    // docs/migrations/2026-10-01_referential_integrity.sql).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_status_changes_ticket"))
    private Ticket ticket;

    public TicketStatusChange() {}    // Required no-argument constructor for JPA

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
