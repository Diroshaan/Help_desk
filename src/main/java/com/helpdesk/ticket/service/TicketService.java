package com.helpdesk.ticket.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.TicketCategories;
import com.helpdesk.ticket.dto.TicketCreateRequest;
import com.helpdesk.ticket.dto.TicketUpdateRequest;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * F2 - Advanced Ticket Request Engine (Chamikara A. K, IT25102416)
 *
 * Business logic for a student's own tickets: submit, view, edit and
 * withdraw. Editing and withdrawing are only allowed while the ticket is
 * still OPEN - once F4's queue engine moves it to IN_PROGRESS or RESOLVED,
 * the student can no longer change it (see Ticket.java).
 */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    @Autowired
    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // Create
    public Ticket createTicket(Long studentId, TicketCreateRequest request) {
        requireValidCategory(request.getCategory());

        Ticket ticket = new Ticket();
        ticket.setStudentId(studentId);
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setCategory(request.getCategory());
        ticket.setPriority(request.getPriority());
        ticket.setStatus(TicketStatus.OPEN);

        return ticketRepository.save(ticket);
    }

    // Read
    public List<Ticket> listByStudent(Long studentId) {
        return ticketRepository.findByStudentId(studentId);
    }

    public Ticket getOwnedTicket(Long ticketId, Long studentId) {
        return findOwnedTicket(ticketId, studentId);
    }

    // Ownership + OPEN check together, for callers (e.g. AttachmentService)
    // whose action is only valid while the ticket is still editable.
    public Ticket getOwnedOpenTicket(Long ticketId, Long studentId) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);
        requireOpen(ticket);
        return ticket;
    }

    // Update - only while OPEN
    public Ticket updateTicket(Long ticketId, Long studentId, TicketUpdateRequest request) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);
        requireOpen(ticket);
        requireValidCategory(request.getCategory());

        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setCategory(request.getCategory());
        ticket.setPriority(request.getPriority());

        return ticketRepository.save(ticket);
    }

    // Withdraw (soft "delete") - only while OPEN
    public Ticket withdrawTicket(Long ticketId, Long studentId) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);
        requireOpen(ticket);

        ticket.setStatus(TicketStatus.WITHDRAWN);
        return ticketRepository.save(ticket);
    }

    private Ticket findOwnedTicket(Long ticketId, Long studentId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (!ticket.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Ticket not found");
        }
        return ticket;
    }

    private void requireOpen(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new ValidationException("This action is only allowed while the ticket is open");
        }
    }

    private void requireValidCategory(String category) {
        if (!TicketCategories.isValid(category)) {
            throw new ValidationException("Invalid category");
        }
    }
}
