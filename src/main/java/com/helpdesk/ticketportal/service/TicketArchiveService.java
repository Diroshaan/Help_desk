package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticketportal.entity.ArchivedTicket;
import com.helpdesk.ticketportal.repository.ArchivedTicketRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketArchiveService {

    private final ArchivedTicketRepository archivedTicketRepository;
    private final TicketRepository ticketRepository;

    @Autowired
    public TicketArchiveService(ArchivedTicketRepository archivedTicketRepository,
                                 TicketRepository ticketRepository) {
        this.archivedTicketRepository = archivedTicketRepository;
        this.ticketRepository = ticketRepository;
    }

    // Only a finished ticket can be archived - matches "archive closed
    // tickets from the active student history view" in the proposal. Both
    // RESOLVED (answered) and WITHDRAWN (cancelled by the student) are final:
    // nothing moves a ticket out of either, so both may leave the active list
    // (#41). CLOSED was removed from TicketStatus as an unreachable state.
    @Transactional
    public ArchivedTicket archiveTicket(Long studentId, Long ticketId) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);

        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.WITHDRAWN) {
            throw new ValidationException("Only a finished ticket (resolved or withdrawn) can be archived");
        }
        if (archivedTicketRepository.existsByStudentIdAndTicketId(studentId, ticketId)) {
            throw new DuplicateResourceException("This ticket is already archived");
        }

        ArchivedTicket archived = new ArchivedTicket();
        archived.setStudentId(studentId);
        archived.setTicketId(ticketId);
        return archivedTicketRepository.save(archived);
    }

    // Ids of the tickets this student has archived. The frontend removes
    // these from F2's GET /api/tickets list (search already excludes them,
    // see StudentTicketQueryService).
    @Transactional(readOnly = true)
    public List<Long> archivedTicketIds(Long studentId) {
        return archivedTicketRepository.findTicketIdsByStudentId(studentId);
    }

    // The archived tickets themselves, for the "Archived" view. Filtered to
    // the caller's own tickets as a second guard: an archive row is always
    // created through findOwnedTicket, but the list should not depend on that.
    @Transactional(readOnly = true)
    public List<Ticket> archivedTickets(Long studentId) {
        return ticketRepository.findAllById(archivedTicketIds(studentId)).stream()
                .filter(ticket -> studentId.equals(ticket.getStudentId()))
                .toList();
    }

    // Restores a ticket back into the active view.
    @Transactional
    public void unarchiveTicket(Long studentId, Long ticketId) {
        ArchivedTicket archived = archivedTicketRepository.findByStudentIdAndTicketId(studentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Archived ticket not found"));
        archivedTicketRepository.delete(archived);
    }

    private Ticket findOwnedTicket(Long ticketId, Long studentId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (!ticket.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Ticket not found");
        }
        return ticket;
    }
}
