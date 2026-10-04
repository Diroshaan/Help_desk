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
import com.helpdesk.ticket.dto.AttachmentResponse;
import com.helpdesk.ticket.dto.TicketStatusChangeResponse;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.service.AttachmentService;
import com.helpdesk.ticket.service.TicketHistoryService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
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
 * Officer endpoints for the support queue. The officer comes from the session,
 * and QueueService limits every lookup to the officer's own departments.
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;
    private final ResolutionService resolutionService;
    private final StaffNoteService staffNoteService;
    private final AppUserRepository appUserRepository;
    private final AttachmentService attachmentService;
    private final TicketHistoryService ticketHistoryService;

    @Autowired
    public QueueController(QueueService queueService, ResolutionService resolutionService,
                            StaffNoteService staffNoteService, AppUserRepository appUserRepository,
                            AttachmentService attachmentService, TicketHistoryService ticketHistoryService) {
        this.queueService = queueService;
        this.resolutionService = resolutionService;
        this.staffNoteService = staffNoteService;
        this.appUserRepository = appUserRepository;
        this.attachmentService = attachmentService;
        this.ticketHistoryService = ticketHistoryService;
    }

    // Filter by department/status, or search by studentId.
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

    @GetMapping("/{ticketId}/resolution/attachment")
    public ResponseEntity<byte[]> downloadResolutionAttachment(@PathVariable Long ticketId,
                                                                 Authentication authentication) {
        Resolution resolution = resolutionService.getByTicketId(currentOfficerId(authentication), ticketId);
        if (resolution.getAttachmentData() == null) {
            throw new ResourceNotFoundException("Resolution has no attachment");
        }
        return fileResponse(resolution.getAttachmentFileType(), resolution.getAttachmentFileName(),
                resolution.getAttachmentData());
    }

    // Department check first, then the student's files.
    @GetMapping("/{ticketId}/attachments")
    public List<AttachmentResponse> listAttachments(@PathVariable Long ticketId, Authentication authentication) {
        queueService.getQueuedTicket(currentOfficerId(authentication), ticketId);
        return attachmentService.listForTicket(ticketId);
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long ticketId, @PathVariable Long attachmentId,
                                                       Authentication authentication) {
        queueService.getQueuedTicket(currentOfficerId(authentication), ticketId);
        Attachment attachment = attachmentService.getForTicket(ticketId, attachmentId);
        return fileResponse(attachment.getFileType(), attachment.getFileName(), attachment.getData());
    }

    @GetMapping("/{ticketId}/history")
    public List<TicketStatusChangeResponse> history(@PathVariable Long ticketId, Authentication authentication) {
        queueService.getQueuedTicket(currentOfficerId(authentication), ticketId);
        return ticketHistoryService.listForTicket(ticketId);
    }

    // ContentDisposition builds the header safely for any file name, and
    // "attachment" makes the file download instead of rendering in our page.
    private ResponseEntity<byte[]> fileResponse(String type, String name, byte[] data) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(data);
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

    // The logged-in user must actually be an Officer, not another kind of account.
    private Long currentOfficerId(Authentication authentication) {
        AppUser user = appUserRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in officer not found"));
        if (!(user instanceof Officer)) {
            throw new ResourceNotFoundException("Logged-in officer not found");
        }
        return user.getId();
    }
}
