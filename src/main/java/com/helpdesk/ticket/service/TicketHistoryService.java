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
 * Records and reads a ticket's status history (TicketStatusChange rows).
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

    // MANDATORY: must join the transaction of the status change it records,
    // so the two commit or roll back together.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long ticketId, TicketStatus from, TicketStatus to, Long changedByUserId) {
        int nextSequence = historyRepository.maxSequence(ticketId) + 1;
        historyRepository.save(new TicketStatusChange(
                ticketId, nextSequence, from, to, changedByUserId, LocalDateTime.now()));
    }

    // Oldest first. Callers must check access to the ticket before calling this.
    @Transactional(readOnly = true)
    public List<TicketStatusChangeResponse> listForTicket(Long ticketId) {
        // Only used to show "Student" as the name, not an ownership check.
        Long studentId = ticketRepository.findById(ticketId).map(Ticket::getStudentId).orElse(null);

        return historyRepository.findByTicketIdOrderBySequenceNoAsc(ticketId).stream()
                .map(change -> new TicketStatusChangeResponse(
                        change.getSequenceNo(), change.getFromStatus(), change.getToStatus(),
                        changedByDisplayName(change.getChangedByUserId(), studentId), change.getChangedAt()))
                .toList();
    }

    // "Student", the officer's name, or "Unknown" - never a raw id.
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
