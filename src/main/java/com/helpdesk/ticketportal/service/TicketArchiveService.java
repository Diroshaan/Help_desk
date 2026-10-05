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

/** Lets a student archive finished tickets to hide them from their active list. */
@Service
public class TicketArchiveService {

    private final ArchivedTicketRepository archivedTicketRepository;
    private final TicketRepository ticketRepository;

    // Spring injects the archive and ticket repositories
    @Autowired
    public TicketArchiveService(ArchivedTicketRepository archivedTicketRepository,
                                 TicketRepository ticketRepository) {
        this.archivedTicketRepository = archivedTicketRepository;
        this.ticketRepository = ticketRepository;
    }

    // Only RESOLVED or WITHDRAWN tickets can be archived - both are final states.
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

    // Returns the ids of the student's archived tickets
    @Transactional(readOnly = true)
    public List<Long> archivedTicketIds(Long studentId) {
        return archivedTicketRepository.findTicketIdsByStudentId(studentId);
    }

    // filtered to the caller's own tickets again as a second guard
    @Transactional(readOnly = true)
    public List<Ticket> archivedTickets(Long studentId) {
        return ticketRepository.findAllById(archivedTicketIds(studentId)).stream()
                .filter(ticket -> studentId.equals(ticket.getStudentId()))
                .toList();
    }

    // Moves an archived ticket back to the student's active list
    @Transactional
    public void unarchiveTicket(Long studentId, Long ticketId) {
        ArchivedTicket archived = archivedTicketRepository.findByStudentIdAndTicketId(studentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Archived ticket not found"));
        archivedTicketRepository.delete(archived);
    }

    // someone else's ticket is a 404, not a 403, so ids can't be probed
    private Ticket findOwnedTicket(Long ticketId, Long studentId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (!ticket.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Ticket not found");
        }
        return ticket;
    }
}
