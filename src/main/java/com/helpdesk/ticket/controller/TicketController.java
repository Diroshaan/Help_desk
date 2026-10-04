package com.helpdesk.ticket.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.entity.Category;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.ticket.dto.AttachmentResponse;
import com.helpdesk.ticket.dto.TicketCreateRequest;
import com.helpdesk.ticket.dto.TicketResponse;
import com.helpdesk.ticket.dto.TicketStatusChangeResponse;
import com.helpdesk.ticket.dto.TicketUpdateRequest;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.service.AttachmentService;
import com.helpdesk.ticket.service.TicketHistoryService;
import com.helpdesk.ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Student endpoints for submitting, viewing, editing and withdrawing tickets,
 * plus their attachments. The student always comes from the session, never the
 * request, and another student's ticket gives 404.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final AttachmentService attachmentService;
    private final TicketHistoryService historyService;
    private final StudentService studentService;
    private final CategoryRepository categoryRepository;

    @Autowired
    public TicketController(TicketService ticketService, AttachmentService attachmentService,
                             TicketHistoryService historyService, StudentService studentService,
                             CategoryRepository categoryRepository) {
        this.ticketService = ticketService;
        this.attachmentService = attachmentService;
        this.historyService = historyService;
        this.studentService = studentService;
        this.categoryRepository = categoryRepository;
    }

    // Categories for the ticket form dropdown, read from the same table the
    // service validates against.
    @GetMapping("/categories")
    public List<String> categories() {
        return categoryRepository.findSelectableWithDepartment().stream()
                .map(Category::getName)
                .toList();
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

    // Ownership check first, then the timeline.
    @GetMapping("/{ticketId}/history")
    public List<TicketStatusChangeResponse> history(@PathVariable Long ticketId, Authentication authentication) {
        ticketService.getOwnedTicket(ticketId, currentStudentId(authentication));
        return historyService.listForTicket(ticketId);
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
        return attachmentService.listByTicket(currentStudentId(authentication), ticketId);
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long ticketId, @PathVariable Long attachmentId,
                                                        Authentication authentication) {
        Attachment attachment = attachmentService.getOwnedAttachment(
                currentStudentId(authentication), ticketId, attachmentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getFileType()))
                // Built with ContentDisposition so odd file names can't break or inject
                // headers; "attachment" makes it download instead of rendering in our page.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(attachment.getFileName(), StandardCharsets.UTF_8)
                                .build().toString())
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
