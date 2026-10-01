package com.helpdesk.ticket.service;

import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.ticket.dto.TicketStatusChangeResponse;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.entity.TicketStatusChange;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.repository.TicketStatusChangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F2 - Advanced Ticket Request Engine (Chamikara A. K, IT25102416)
 *
 * Records and reads a ticket's status-change history (#45, the weak entity
 * TicketStatusChange - see that class's own Javadoc).
 */
@Service
public class TicketHistoryService {

    private final TicketStatusChangeRepository historyRepository;
    private final TicketRepository ticketRepository;
    private final OfficerRepository officerRepository;

    @Autowired
    public TicketHistoryService(TicketStatusChangeRepository historyRepository, TicketRepository ticketRepository,
                                 OfficerRepository officerRepository) {
        this.historyRepository = historyRepository;
        this.ticketRepository = ticketRepository;
        this.officerRepository = officerRepository;
    }

    /**
     * Appends one row to a ticket's history, numbered by sequence within
     * that ticket.
     *
     * MANDATORY, not the REQUIRED default: this must never open its own
     * transaction. It has to run inside the SAME transaction as the status
     * change it is recording (TicketService.createTicket/withdrawTicket, or
     * F4's QueueService via TicketHistoryRecorder), so the sequence number
     * and the change itself commit or roll back together. If this opened a
     * new transaction, a rollback of the status change could leave an
     * orphaned history row behind, or the reverse.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long ticketId, TicketStatus from, TicketStatus to, Long changedByUserId) {
        int nextSequence = historyRepository.maxSequence(ticketId) + 1;
        historyRepository.save(new TicketStatusChange(
                ticketId, nextSequence, from, to, changedByUserId, LocalDateTime.now()));
    }

    /**
     * The timeline for one ticket, oldest first.
     *
     * No ownership check: the caller must already have proved access to the
     * ticket (contract C3; TicketController calls getOwnedTicket first, F4's
     * QueueController scopes through QueueService.getQueuedTicket first).
     */
    @Transactional(readOnly = true)
    public List<TicketStatusChangeResponse> listForTicket(Long ticketId) {
        // Only to resolve "Student" below - this is a data lookup for the
        // display name, not an ownership check (the caller already did that).
        Long studentId = ticketRepository.findById(ticketId).map(Ticket::getStudentId).orElse(null);

        return historyRepository.findByTicketIdOrderBySequenceNoAsc(ticketId).stream()
                .map(change -> new TicketStatusChangeResponse(
                        change.getSequenceNo(), change.getFromStatus(), change.getToStatus(),
                        changedByDisplayName(change.getChangedByUserId(), studentId), change.getChangedAt()))
                .toList();
    }

    // "Student", the officer's full name, or "Unknown" - never a bare id,
    // which would mean nothing to the student reading their own timeline.
    private String changedByDisplayName(Long changedByUserId, Long studentId) {
        if (changedByUserId == null) {
            return "Unknown";
        }
        if (changedByUserId.equals(studentId)) {
            return "Student";
        }
        return officerRepository.findById(changedByUserId)
                .map(Officer::getFullName)
                .orElse("Unknown");
    }
}
