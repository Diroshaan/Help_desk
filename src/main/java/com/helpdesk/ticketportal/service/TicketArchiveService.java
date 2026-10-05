package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticketportal.entity.ArchivedTicket;
import com.helpdesk.ticketportal.repository.ArchivedTicketRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Lets a student archive finished tickets to hide them from their active list. */
@Service
public class TicketArchiveService {

    private final ArchivedTicketRepository archivedTicketRepository;
    private final TicketRepository ticketRepository;
    private final TicketService ticketService;

    // Spring injects the archive and ticket repositories and the ticket service
    @Autowired
    public TicketArchiveService(ArchivedTicketRepository archivedTicketRepository,
                                 TicketRepository ticketRepository,
                                 TicketService ticketService) {
        this.archivedTicketRepository = archivedTicketRepository;
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
    }

    // Only RESOLVED or WITHDRAWN tickets can be archived - both are final states.
    @Transactional
    public ArchivedTicket archiveTicket(Long studentId, Long ticketId) {
        Ticket ticket = ticketService.getOwnedTicket(ticketId, studentId);

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
        List<Ticket> result = new ArrayList<>();
        for (Ticket ticket : ticketRepository.findAllById(archivedTicketIds(studentId))) {
            if (ticket.getStudentId().equals(studentId)) {
                result.add(ticket);
            }
        }
        return result;
    }

    // Moves an archived ticket back to the student's active list
    @Transactional
    public void unarchiveTicket(Long studentId, Long ticketId) {
        ArchivedTicket archived = archivedTicketRepository.findByStudentIdAndTicketId(studentId, ticketId).orElse(null);
        if (archived == null) {
            throw new ResourceNotFoundException("Archived ticket not found");
        }
        archivedTicketRepository.delete(archived);
    }
}
