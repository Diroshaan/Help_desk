package com.helpdesk.ticket.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.notification.event.TicketSubmittedEvent;
import com.helpdesk.queue.service.RoutingService;
import com.helpdesk.ticket.dto.TicketCreateRequest;
import com.helpdesk.ticket.dto.TicketUpdateRequest;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A student's own tickets: submit, view, edit and withdraw.
 * Edit and withdraw only work while the ticket is OPEN.
 */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;

    // Observer publisher: we publish TicketSubmittedEvent and the notification
    // module decides who gets told.
    private final ApplicationEventPublisher eventPublisher;

    // Student changes (create, withdraw) are recorded here directly; officer
    // changes reach the history through TicketHistoryRecorder.
    private final TicketHistoryService historyService;

    // Picks the department from the ticket's category.
    private final RoutingService routingService;

    @Autowired
    public TicketService(TicketRepository ticketRepository, CategoryRepository categoryRepository,
                         ApplicationEventPublisher eventPublisher, TicketHistoryService historyService,
                         RoutingService routingService) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
        this.historyService = historyService;
        this.routingService = routingService;
    }

    @Transactional
    public Ticket createTicket(Long studentId, TicketCreateRequest request) {
        requireValidCategory(request.getCategory());

        Ticket ticket = new Ticket();
        ticket.setStudentId(studentId);
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setCategory(request.getCategory());
        ticket.setPriority(request.getPriority());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setAssignedDepartmentId(routingService.departmentFor(request.getCategory()));

        Ticket saved = ticketRepository.save(ticket);

        // First history row has no "from" status.
        historyService.record(saved.getId(), null, TicketStatus.OPEN, studentId);

        // Published after the save so it has the real id; listeners run after commit.
        eventPublisher.publishEvent(new TicketSubmittedEvent(
                saved.getId(), saved.getSubject(), saved.getCategory(), saved.getAssignedDepartmentId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByStudent(Long studentId) {
        return ticketRepository.findByStudentId(studentId);
    }

    @Transactional(readOnly = true)
    public Ticket getOwnedTicket(Long ticketId, Long studentId) {
        return findOwnedTicket(ticketId, studentId);
    }

    // Ownership and OPEN check together, e.g. for attachment upload/delete.
    @Transactional(readOnly = true)
    public Ticket getOwnedOpenTicket(Long ticketId, Long studentId) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);
        requireOpen(ticket);
        return ticket;
    }

    @Transactional
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

    // Withdrawing is a soft delete: the ticket stays, with status WITHDRAWN.
    @Transactional
    public Ticket withdrawTicket(Long ticketId, Long studentId) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);
        requireOpen(ticket);

        ticket.setStatus(TicketStatus.WITHDRAWN);
        Ticket saved = ticketRepository.save(ticket);

        historyService.record(ticketId, TicketStatus.OPEN, TicketStatus.WITHDRAWN, studentId);
        return saved;
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

    // Checked against the categories table, so new categories need no code change.
    private void requireValidCategory(String category) {
        if (!categoryRepository.existsByName(category)) {
            throw new ValidationException("Invalid category");
        }
    }
}
