package com.helpdesk.ticket.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.notification.event.TicketSubmittedEvent;
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
    private final CategoryRepository categoryRepository;

    // Added with the new-ticket alert for officers (US-04), agreed with
    // Chamikara: this service only ANNOUNCES that a ticket was submitted. Who
    // gets told, and how, is decided in the notification module
    // (QueueArrivalNotifier + the channels) - the Observer pattern, the same
    // way QueueService announces status changes.
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TicketService(TicketRepository ticketRepository, CategoryRepository categoryRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
    }

    // Create
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

        Ticket saved = ticketRepository.save(ticket);

        // After the save, so the event carries the real ticket id. The
        // listener runs only once the ticket is committed (AFTER_COMMIT), so
        // a submission that fails never alerts anybody.
        eventPublisher.publishEvent(new TicketSubmittedEvent(
                saved.getId(), saved.getSubject(), saved.getCategory(), saved.getAssignedDepartmentId()));
        return saved;
    }

    // Read
    @Transactional(readOnly = true)
    public List<Ticket> listByStudent(Long studentId) {
        return ticketRepository.findByStudentId(studentId);
    }

    @Transactional(readOnly = true)
    public Ticket getOwnedTicket(Long ticketId, Long studentId) {
        return findOwnedTicket(ticketId, studentId);
    }

    // Ownership + OPEN check together, for callers (e.g. AttachmentService)
    // whose action is only valid while the ticket is still editable.
    @Transactional(readOnly = true)
    public Ticket getOwnedOpenTicket(Long ticketId, Long studentId) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);
        requireOpen(ticket);
        return ticket;
    }

    // Update - only while OPEN
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

    // Withdraw (soft "delete") - only while OPEN
    @Transactional
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

    // CHANGED during the F4 shared-reference-data merge (feature/f4-RESPONSE):
    // this used to call ticket.TicketCategories.isValid(category), a hardcoded
    // in-memory list. Now that common.reference.entity.Category is a real,
    // seeded table, validation checks against it instead so a category added
    // there is recognised without a code change here.
    // FLAG FOR F2 REVIEW (Chamikara): this changes validation behavior, not
    // just its wording - TicketCategories.ALL and the seeded category names
    // happen to match today, but they are no longer the same source of truth,
    // and TicketController's GET dropdown endpoint still reads TicketCategories.ALL
    // directly. Please confirm this is the intended replacement before merging.
    private void requireValidCategory(String category) {
        if (!categoryRepository.existsByName(category)) {
            throw new ValidationException("Invalid category");
        }
    }
}
