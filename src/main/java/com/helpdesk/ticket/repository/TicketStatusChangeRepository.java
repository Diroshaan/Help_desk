package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.TicketStatusChange;
import com.helpdesk.ticket.entity.TicketStatusChangeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketStatusChangeRepository extends JpaRepository<TicketStatusChange, TicketStatusChangeId> {

    List<TicketStatusChange> findByTicketIdOrderBySequenceNoAsc(Long ticketId);

    // 0 when there is no history yet, so the first row gets sequence 1.
    @Query("SELECT COALESCE(MAX(c.sequenceNo), 0) FROM TicketStatusChange c WHERE c.ticketId = :ticketId")
    int maxSequence(@Param("ticketId") Long ticketId);
}
