package com.helpdesk.ticket.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.ticket.TicketCategories;
import com.helpdesk.ticket.dto.AttachmentResponse;
import com.helpdesk.ticket.dto.TicketCreateRequest;
import com.helpdesk.ticket.dto.TicketResponse;
import com.helpdesk.ticket.dto.TicketUpdateRequest;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.service.AttachmentService;
import com.helpdesk.ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * F2 - Advanced Ticket Request Engine (Chamikara A. K, IT25102416)
 *
 * REST endpoints for a student submitting, viewing, editing and withdrawing
 * their own tickets, plus attaching supporting files to them. Every endpoint
 * resolves the acting student from the authenticated session (never from the
 * request body) - see currentStudentId, same approach as FeedbackController.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final AttachmentService attachmentService;
    private final StudentService studentService;

    @Autowired
    public TicketController(TicketService ticketService, AttachmentService attachmentService,
                             StudentService studentService) {
        this.ticketService = ticketService;
        this.attachmentService = attachmentService;
        this.studentService = studentService;
    }

    // Shared list of valid categories, used by the frontend to populate the
    // create/edit ticket dropdown (see TicketCategories).
    @GetMapping("/categories")
    public List<String> categories() {
        return TicketCategories.ALL;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request,
                                                   Authentication authentication) {
        Ticket ticket = ticketService.createTicket(currentStudentId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(ticket));
    }

    @GetMapping
    public List<TicketResponse> listMine(Authentication authentication) {
        return ticketService.listByStudent(currentStudentId(authentication)).stream()
                .map(TicketResponse::from)
                .toList();
    }

    @GetMapping("/{ticketId}")
    public TicketResponse getOne(@PathVariable Long ticketId, Authentication authentication) {
        Ticket ticket = ticketService.getOwnedTicket(ticketId, currentStudentId(authentication));
        return TicketResponse.from(ticket);
    }

    @PutMapping("/{ticketId}")
    public TicketResponse update(@PathVariable Long ticketId, @Valid @RequestBody TicketUpdateRequest request,
                                   Authentication authentication) {
        Ticket ticket = ticketService.updateTicket(ticketId, currentStudentId(authentication), request);
        return TicketResponse.from(ticket);
    }

    @PostMapping("/{ticketId}/withdraw")
    public TicketResponse withdraw(@PathVariable Long ticketId, Authentication authentication) {
        Ticket ticket = ticketService.withdrawTicket(ticketId, currentStudentId(authentication));
        return TicketResponse.from(ticket);
    }

    @PostMapping(value = "/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(@PathVariable Long ticketId,
                                                                  @RequestParam("file") MultipartFile file,
                                                                  Authentication authentication) {
        Attachment attachment = attachmentService.upload(currentStudentId(authentication), ticketId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(AttachmentResponse.from(attachment));
    }

    @GetMapping("/{ticketId}/attachments")
    public List<AttachmentResponse> listAttachments(@PathVariable Long ticketId, Authentication authentication) {
        return attachmentService.listByTicket(currentStudentId(authentication), ticketId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long ticketId, @PathVariable Long attachmentId,
                                                        Authentication authentication) {
        Attachment attachment = attachmentService.getOwnedAttachment(
                currentStudentId(authentication), ticketId, attachmentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + attachment.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(attachment.getData());
    }

    @DeleteMapping("/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long ticketId, @PathVariable Long attachmentId,
                                                    Authentication authentication) {
        attachmentService.delete(currentStudentId(authentication), ticketId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    private Long currentStudentId(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in student not found"));
    }
}
