package com.helpdesk.queue.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Business logic for officers working the support queue: listing/searching
 * tickets, moving a ticket through its lifecycle, and (re)assigning it.
 *
 * Every lookup here is scoped to the REQUESTING officer's own department(s).
 * SecurityConfig's hasRole("OFFICER") on /api/queue/** only proves the
 * caller is SOME officer, not that they're allowed to see or touch THIS
 * ticket - that second check (is the caller actually affiliated with the
 * department this ticket is routed to) is the RBAC-beyond-role check done
 * in requireOfficerInTicketDepartment, on every method below.
 *
 * Department scoping is a MEMBERSHIP check, not an equality check: an
 * officer now serves a Set<Department> (common.user.entity.Officer.departments,
 * the shared @ManyToMany added for F4), not a single departmentId, so "is
 * this officer allowed to touch this ticket's department" means the
 * ticket's department code must appear somewhere in that set.
 *
 * One deliberate exception: a ticket with no assignedDepartmentId yet
 * (fresh from F2, never routed) is visible/assignable to ANY active
 * officer. There is no department to scope it to until someone routes it,
 * and gating an unrouted ticket behind "you must already be in its
 * department" would make it impossible for anyone to ever triage it into
 * one. Once a department is set, only that department's officers may act
 * on it (including transferring it elsewhere).
 */
@Service
public class QueueService {

    // OPEN can only move to IN_PROGRESS via this service. RESOLVED is
    // reached only through ResolutionService (see updateStatus below), and
    // WITHDRAWN is the student's own call (TicketService), not the queue's -
    // so neither appears as a reachable target here.
    private static final Map<TicketStatus, List<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
            TicketStatus.OPEN, List.of(TicketStatus.IN_PROGRESS),
            TicketStatus.IN_PROGRESS, List.of(),
            TicketStatus.RESOLVED, List.of(),
            TicketStatus.WITHDRAWN, List.of()
    );

    private final TicketRepository ticketRepository;
    private final OfficerRepository officerRepository;
    private final DepartmentRepository departmentRepository;

    @Autowired
    public QueueService(TicketRepository ticketRepository, OfficerRepository officerRepository,
                         DepartmentRepository departmentRepository) {
        this.ticketRepository = ticketRepository;
        this.officerRepository = officerRepository;
        this.departmentRepository = departmentRepository;
    }

    // Retrieval -----------------------------------------------------------

    // departmentId/status are optional filters; a null departmentId defaults
    // to every department the caller serves, since an officer has no
    // business browsing a department they don't serve by default.
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

        return officerDepartmentCodes.stream()
                .flatMap(code -> ticketRepository.findByAssignedDepartmentId(code).stream())
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

    // Search by ticket id is just getQueuedTicket(); this covers "search by
    // student id" instead - every ticket a given student has raised that
    // this officer is allowed to see (their own departments', plus any
    // still-unrouted ticket).
    @Transactional(readOnly = true)
    public List<Ticket> searchByStudent(Long officerId, Long studentId) {
        Officer officer = requireActiveOfficer(officerId);
        Set<String> officerDepartmentCodes = departmentCodesOf(officer);
        return ticketRepository.findByStudentId(studentId).stream()
                .filter(ticket -> ticket.getAssignedDepartmentId() == null
                        || officerDepartmentCodes.contains(ticket.getAssignedDepartmentId()))
                .toList();
    }

    // Assignment ------------------------------------------------------------

    @Transactional
    public Ticket assignTicket(Long officerId, Long ticketId, String targetDepartmentId, Long targetOfficerId) {
        Officer officer = requireActiveOfficer(officerId);
        Ticket ticket = findTicket(ticketId);
        requireOfficerInTicketDepartment(officer, ticket);

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
                // The officer serves zero or several departments - there is
                // no single one to infer, so the caller must say which.
                throw new ValidationException(
                        "departmentId is required when the target officer serves more than one department");
            }
        } else if (!departmentRepository.existsById(resolvedDepartmentId)) {
            throw new ResourceNotFoundException("Department not found");
        }

        ticket.setAssignedDepartmentId(resolvedDepartmentId);
        ticket.setAssignedOfficerId(targetOfficerId);
        ticket.setAssignedAt(LocalDateTime.now());
        return ticketRepository.save(ticket);
    }

    // Status transitions ------------------------------------------------------

    @Transactional
    public Ticket updateStatus(Long officerId, Long ticketId, TicketStatus targetStatus) {
        Officer officer = requireActiveOfficer(officerId);
        Ticket ticket = findTicket(ticketId);
        requireOfficerInTicketDepartment(officer, ticket);

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

        ticket.setStatus(targetStatus);
        return ticketRepository.save(ticket);
    }

    // Called by ResolutionService, which owns the IN_PROGRESS -> RESOLVED
    // transition (it always happens together with saving the resolution
    // text, never on its own). Package-private and deliberately not
    // @Transactional itself - it always runs inside a transaction already
    // opened by the calling ResolutionService method, and a non-public
    // method wouldn't be proxied by Spring's transaction advice anyway.
    Ticket markResolved(Ticket ticket) {
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolvedAt(LocalDateTime.now());
        return ticketRepository.save(ticket);
    }

    // Called by ResolutionService.revoke to reopen a ticket whose resolution
    // was pulled back before final closure. Same non-@Transactional
    // reasoning as markResolved above.
    Ticket reopen(Ticket ticket) {
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setResolvedAt(null);
        return ticketRepository.save(ticket);
    }

    // Shared lookups ----------------------------------------------------------

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
            // 404, not 403: an officer outside the department shouldn't be
            // able to tell "doesn't exist" apart from "exists but isn't
            // yours" - same reasoning as TicketService.findOwnedTicket.
            throw new ResourceNotFoundException("Ticket not found");
        }
    }
}
