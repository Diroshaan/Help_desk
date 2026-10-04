package com.helpdesk.ticketportal.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.queue.entity.Resolution;
import com.helpdesk.ticketportal.dto.TicketResolutionResponse;
import com.helpdesk.ticketportal.service.TicketResolutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/** Lets a student read the answer to their own ticket and download its attachment. */
@RestController
@RequestMapping("/api/tickets/{ticketId}/resolution")
public class TicketResolutionController {

    private final TicketResolutionService ticketResolutionService;
    private final StudentService studentService;

    @Autowired
    public TicketResolutionController(TicketResolutionService ticketResolutionService,
                                       StudentService studentService) {
        this.ticketResolutionService = ticketResolutionService;
        this.studentService = studentService;
    }

    @GetMapping
    public TicketResolutionResponse get(@PathVariable Long ticketId, Authentication authentication) {
        return ticketResolutionService.getForStudent(ticketId, currentStudentId(authentication));
    }

    @GetMapping("/attachment")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long ticketId, Authentication authentication) {
        Resolution resolution = ticketResolutionService.getForStudentWithFile(
                ticketId, currentStudentId(authentication));

        // Download as an attachment (not inline) with nosniff; ContentDisposition
        // handles quotes and non-English file names safely.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resolution.getAttachmentFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(resolution.getAttachmentFileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resolution.getAttachmentData());
    }

    private Long currentStudentId(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in student not found"));
    }
}
