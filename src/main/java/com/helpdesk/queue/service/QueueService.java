package com.helpdesk.queue.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.notification.event.TicketStatusChangedEvent;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Officer side of the queue: list and search tickets, move them through their
 * status, and (re)assign them.
 * Department scoping: officers only see tickets routed to their own departments;
 * anything else is a 404. Unrouted tickets are visible to every officer for triage.
 */
@Service
public class QueueService {

    // Only OPEN -> IN_PROGRESS happens here. RESOLVED goes through ResolutionService
    // and WITHDRAWN is the student's action.
    private static final Map<TicketStatus, List<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
            TicketStatus.OPEN, List.of(TicketStatus.IN_PROGRESS),
            TicketStatus.IN_PROGRESS, List.of(),
            TicketStatus.RESOLVED, List.of(),
            TicketStatus.WITHDRAWN, List.of()
    );

    private final TicketRepository ticketRepository;
    private final OfficerRepository officerRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public QueueService(TicketRepository ticketRepository, OfficerRepository officerRepository,
                         DepartmentRepository departmentRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.officerRepository = officerRepository;
        this.departmentRepository = departmentRepository;
        this.eventPublisher = eventPublisher;
    }

    // Without a department filter: unrouted tickets plus all of the officer's
    // departments. With one: only that department, and it must be one of theirs.
    @Transactional(readOnly = true)
    public List<Ticket> findQueue(Long officerId, String departmentId, TicketStatus status) {
        Officer officer = requireActiveOfficer(officerId);
        Set<String> officerDepartmentCodes = departmentCodesOf(officer);

        if (departmentId != null) {
            if (!officerDepartmentCodes.contains(departmentId)) {
                throw new ResourceNotFoundException("Department not found");
            }
            return ticketRepository.findByAssignedDepartmentId(departmentId).stream()
                    .filter(ticket -> status == null || status == ticket.getStatus())
                    .toList();
        }

        return Stream.concat(
                        ticketRepository.findByAssignedDepartmentIdIsNull().stream(),
                        officerDepartmentCodes.stream()
                                .flatMap(code -> ticketRepository.findByAssignedDepartmentId(code).stream()))
                .filter(ticket -> status == null || status == ticket.getStatus())
                .toList();
    }

    @Transactional(readOnly = true)
    public Ticket getQueuedTicket(Long officerId, Long ticketId) {
        Officer officer = requireActiveOfficer(officerId);
        Ticket ticket = findTicket(ticketId);
        requireOfficerInTicketDepartment(officer, ticket);
        return ticket;
    }

    // A student's tickets, limited to what this officer may see.
    @Transactional(readOnly = true)
    public List<Ticket> searchByStudent(Long officerId, Long studentId) {
        Officer officer = requireActiveOfficer(officerId);
        Set<String> officerDepartmentCodes = departmentCodesOf(officer);
        return ticketRepository.findByStudentId(studentId).stream()
                .filter(ticket -> ticket.getAssignedDepartmentId() == null
                        || officerDepartmentCodes.contains(ticket.getAssignedDepartmentId()))
                .toList();
    }

    // For changing status, resolving or adding notes. Unlike reads, this needs
    // the ticket to be routed to a department first.
    Ticket getWorkableTicket(Long officerId, Long ticketId) {
        Officer officer = requireActiveOfficer(officerId);
        Ticket ticket = findTicket(ticketId);
        if (ticket.getAssignedDepartmentId() == null) {
            throw new ValidationException("Route this ticket to a department first");
        }
        requireOfficerInTicketDepartment(officer, ticket);
        return ticket;
    }

    @Transactional
    public Ticket assignTicket(Long officerId, Long ticketId, String targetDepartmentId, Long targetOfficerId) {
        Officer officer = requireActiveOfficer(officerId);
        Ticket ticket = findTicket(ticketId);
        requireOfficerInTicketDepartment(officer, ticket);

        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.WITHDRAWN) {
            throw new ValidationException("A " + ticket.getStatus() + " ticket cannot be re-routed");
        }

        if (targetOfficerId == null && targetDepartmentId == null) {
            throw new ValidationException("Either officerId or departmentId is required");
        }

        String resolvedDepartmentId = targetDepartmentId;
        if (targetOfficerId != null) {
            Officer targetOfficer = requireActiveOfficer(targetOfficerId);
            Set<String> targetOfficerDepartmentCodes = departmentCodesOf(targetOfficer);

            if (resolvedDepartmentId != null) {
                if (!targetOfficerDepartmentCodes.contains(resolvedDepartmentId)) {
                    throw new ValidationException("officerId does not belong to departmentId");
                }
            } else if (targetOfficerDepartmentCodes.size() == 1) {
                resolvedDepartmentId = targetOfficerDepartmentCodes.iterator().next();
            } else {
                // Can't guess the department if the officer serves zero or several.
                throw new ValidationException(
                        "departmentId is required when the target officer serves more than one department");
            }
        } else if (!departmentRepository.existsById(resolvedDepartmentId)) {
            throw new ResourceNotFoundException("Department not found");
        }

        // An in-progress ticket moved to another department needs a new owner.
        if (ticket.getStatus() == TicketStatus.IN_PROGRESS && targetOfficerId == null
                && ticket.getAssignedDepartmentId() != null
                && !resolvedDepartmentId.equals(ticket.getAssignedDepartmentId())) {
            throw new ValidationException(
                    "Moving an in-progress ticket to another department needs a target officer");
        }

        ticket.setAssignedDepartmentId(resolvedDepartmentId);
        ticket.setAssignedOfficerId(targetOfficerId);
        ticket.setAssignedAt(LocalDateTime.now());
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket updateStatus(Long officerId, Long ticketId, TicketStatus targetStatus) {
        Ticket ticket = getWorkableTicket(officerId, ticketId);

        if (targetStatus == TicketStatus.RESOLVED) {
            throw new ValidationException(
                    "Resolve a ticket by posting a resolution (POST /api/queue/{id}/resolution), "
                            + "not by setting its status directly");
        }

        TicketStatus currentStatus = ticket.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, List.of()).contains(targetStatus)) {
            throw new ValidationException(
                    "Cannot move a ticket from " + currentStatus + " to " + targetStatus);
        }

        if (targetStatus == TicketStatus.IN_PROGRESS) {
            Long owner = ticket.getAssignedOfficerId();
            if (owner == null) {
                // Claim on pick-up: the officer who starts it becomes the owner.
                ticket.setAssignedOfficerId(officerId);
                ticket.setAssignedAt(LocalDateTime.now());
            } else if (!owner.equals(officerId)) {
                throw new ValidationException("This ticket is assigned to another officer");
            }
        }

        ticket.setStatus(targetStatus);
        Ticket saved = ticketRepository.save(ticket);
        publishStatusChange(saved, currentStatus, officerId);
        return saved;
    }

    // Only called from ResolutionService, inside its transaction.
    Ticket markResolved(Ticket ticket, Long officerId) {
        TicketStatus previous = ticket.getStatus();
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(LocalDateTime.now());
        Ticket saved = ticketRepository.save(ticket);
        publishStatusChange(saved, previous, officerId);
        return saved;
    }

    // Used when a resolution is revoked.
    Ticket reopen(Ticket ticket, Long officerId) {
        TicketStatus previous = ticket.getStatus();
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setResolvedAt(null);
        Ticket saved = ticketRepository.save(ticket);
        publishStatusChange(saved, previous, officerId);
        return saved;
    }

    // Observer publisher: announces TicketStatusChangedEvent without knowing who
    // listens (the student notifier and TicketHistoryRecorder do).
    private void publishStatusChange(Ticket ticket, TicketStatus previous, Long officerId) {
        eventPublisher.publishEvent(new TicketStatusChangedEvent(
                ticket.getId(), ticket.getStudentId(), ticket.getSubject(), previous, ticket.getStatus(), officerId));
    }

    private Ticket findTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private Officer requireActiveOfficer(Long officerId) {
        return officerRepository.findByIdAndActive(officerId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Officer not found"));
    }

    private Set<String> departmentCodesOf(Officer officer) {
        return officer.getDepartments().stream()
                .map(Department::getCode)
                .collect(Collectors.toSet());
    }

    private void requireOfficerInTicketDepartment(Officer officer, Ticket ticket) {
        String ticketDepartmentId = ticket.getAssignedDepartmentId();
        if (ticketDepartmentId != null && !departmentCodesOf(officer).contains(ticketDepartmentId)) {
            // 404 rather than 403, so other departments' ticket ids can't be probed.
            throw new ResourceNotFoundException("Ticket not found");
        }
    }
}
