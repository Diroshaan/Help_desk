package com.helpdesk.queue.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.queue.dto.ResolutionRequest;
import com.helpdesk.queue.dto.ResolutionResponse;
import com.helpdesk.queue.dto.StaffNoteRequest;
import com.helpdesk.queue.dto.StaffNoteResponse;
import com.helpdesk.queue.dto.TicketAssignmentRequest;
import com.helpdesk.queue.dto.TicketQueueDetailResponse;
import com.helpdesk.queue.dto.TicketQueueResponse;
import com.helpdesk.queue.dto.TicketStatusUpdateRequest;
import com.helpdesk.queue.entity.Resolution;
import com.helpdesk.queue.entity.StaffNote;
import com.helpdesk.queue.service.QueueService;
import com.helpdesk.queue.service.ResolutionService;
import com.helpdesk.queue.service.StaffNoteService;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * REST endpoints for officers working the support queue. Every endpoint
 * resolves the acting officer from the authenticated session (never from
 * the request body) - see currentOfficerId, same approach as
 * TicketController.currentStudentId. This path is already restricted to
 * hasRole("OFFICER") by SecurityConfig; QueueService additionally scopes
 * every lookup to the caller's own department (see its class comment).
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;
    private final ResolutionService resolutionService;
    private final StaffNoteService staffNoteService;
    private final AppUserRepository appUserRepository;

    @Autowired
    public QueueController(QueueService queueService, ResolutionService resolutionService,
                            StaffNoteService staffNoteService, AppUserRepository appUserRepository) {
        this.queueService = queueService;
        this.resolutionService = resolutionService;
        this.staffNoteService = staffNoteService;
        this.appUserRepository = appUserRepository;
    }

    // List/filter (departmentId/status) or search (studentId) the queue.
    @GetMapping
    public List<TicketQueueResponse> list(@RequestParam(required = false) String departmentId,
                                           @RequestParam(required = false) TicketStatus status,
                                           @RequestParam(required = false) Long studentId,
                                           Authentication authentication) {
        Long officerId = currentOfficerId(authentication);
        List<Ticket> tickets = (studentId != null)
                ? queueService.searchByStudent(officerId, studentId)
                : queueService.findQueue(officerId, departmentId, status);
        return tickets.stream().map(TicketQueueResponse::from).toList();
    }

    // Detail + history (the ticket's own timestamps) + resolution + notes.
    @GetMapping("/{ticketId}")
    public TicketQueueDetailResponse getOne(@PathVariable Long ticketId, Authentication authentication) {
        Long officerId = currentOfficerId(authentication);
        Ticket ticket = queueService.getQueuedTicket(officerId, ticketId);
        ResolutionResponse resolution = resolutionService.findByTicketIdOptional(officerId, ticketId)
                .map(ResolutionResponse::from)
                .orElse(null);
        List<StaffNoteResponse> notes = staffNoteService.listByTicket(officerId, ticketId).stream()
                .map(StaffNoteResponse::from)
                .toList();

        return new TicketQueueDetailResponse(TicketQueueResponse.from(ticket), resolution, notes);
    }

    @PutMapping("/{ticketId}/status")
    public TicketQueueResponse updateStatus(@PathVariable Long ticketId,
                                             @Valid @RequestBody TicketStatusUpdateRequest request,
                                             Authentication authentication) {
        Ticket ticket = queueService.updateStatus(currentOfficerId(authentication), ticketId, request.getStatus());
        return TicketQueueResponse.from(ticket);
    }

    @PutMapping("/{ticketId}/assign")
    public TicketQueueResponse assign(@PathVariable Long ticketId,
                                       @RequestBody TicketAssignmentRequest request,
                                       Authentication authentication) {
        Ticket ticket = queueService.assignTicket(currentOfficerId(authentication), ticketId,
                request.getDepartmentId(), request.getOfficerId());
        return TicketQueueResponse.from(ticket);
    }

    @PostMapping(value = "/{ticketId}/resolution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResolutionResponse> createResolution(@PathVariable Long ticketId,
                                                                 @ModelAttribute ResolutionRequest request,
                                                                 Authentication authentication) {
        Resolution resolution = resolutionService.create(currentOfficerId(authentication), ticketId,
                request.getResponseText(), request.getAttachment());
        return ResponseEntity.status(HttpStatus.CREATED).body(ResolutionResponse.from(resolution));
    }

    @PutMapping(value = "/{ticketId}/resolution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResolutionResponse editResolution(@PathVariable Long ticketId,
                                              @ModelAttribute ResolutionRequest request,
                                              Authentication authentication) {
        Resolution resolution = resolutionService.edit(currentOfficerId(authentication), ticketId,
                request.getResponseText(), request.getAttachment());
        return ResolutionResponse.from(resolution);
    }

    @DeleteMapping("/{ticketId}/resolution")
    public ResponseEntity<Void> revokeResolution(@PathVariable Long ticketId, Authentication authentication) {
        resolutionService.revoke(currentOfficerId(authentication), ticketId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ticketId}/notes")
    public ResponseEntity<StaffNoteResponse> addNote(@PathVariable Long ticketId,
                                                      @Valid @RequestBody StaffNoteRequest request,
                                                      Authentication authentication) {
        StaffNote note = staffNoteService.create(currentOfficerId(authentication), ticketId, request.getNote());
        return ResponseEntity.status(HttpStatus.CREATED).body(StaffNoteResponse.from(note));
    }

    @DeleteMapping("/{ticketId}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long ticketId, @PathVariable Long noteId,
                                            Authentication authentication) {
        staffNoteService.delete(currentOfficerId(authentication), ticketId, noteId);
        return ResponseEntity.noContent().build();
    }

    // Officer accounts are now a shared common.user.entity.Officer row (see
    // that class's javadoc) rather than a Student with role=OFFICER, so the
    // current officer's id is resolved by looking up the polymorphic AppUser
    // by login email and confirming the row that comes back is actually an
    // Officer - not, say, a Student or Administrator who somehow reached an
    // OFFICER-only endpoint.
    private Long currentOfficerId(Authentication authentication) {
        AppUser user = appUserRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in officer not found"));
        if (!(user instanceof Officer)) {
            throw new ResourceNotFoundException("Logged-in officer not found");
        }
        return user.getId();
    }
}
