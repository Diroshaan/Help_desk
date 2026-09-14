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

    // Only a resolved ticket can be archived - matches "archive closed
    // tickets from the active student history view" in the proposal, using
    // RESOLVED as the terminal status (see TicketStatus - CLOSED was removed
    // as an unused/unreachable state).
    public ArchivedTicket archiveTicket(Long studentId, Long ticketId) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);

        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new ValidationException("Only resolved tickets can be archived");
        }
        if (archivedTicketRepository.existsByStudentIdAndTicketId(studentId, ticketId)) {
            throw new DuplicateResourceException("This ticket is already archived");
        }

        ArchivedTicket archived = new ArchivedTicket();
        archived.setStudentId(studentId);
        archived.setTicketId(ticketId);
        return archivedTicketRepository.save(archived);
    }

    // Restores a ticket back into the active view.
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
