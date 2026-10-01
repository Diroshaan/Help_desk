package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.TicketStatusChange;
import com.helpdesk.ticket.entity.TicketStatusChangeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketStatusChangeRepository extends JpaRepository<TicketStatusChange, TicketStatusChangeId> {

    List<TicketStatusChange> findByTicketIdOrderBySequenceNoAsc(Long ticketId);

    // COALESCE to 0 so a ticket with no history yet (every ticket, before
    // its first row) gives "the next sequence number is 1" instead of null.
    @Query("SELECT COALESCE(MAX(c.sequenceNo), 0) FROM TicketStatusChange c WHERE c.ticketId = :ticketId")
    int maxSequence(@Param("ticketId") Long ticketId);
}
