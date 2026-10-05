package com.helpdesk.ticketportal.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.ticketportal.service.TicketArchiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Archive and unarchive one of the student's own tickets. */
@RestController
@RequestMapping("/api/tickets/{ticketId}/archive")
public class TicketArchiveController {

    private final TicketArchiveService ticketArchiveService;
    private final StudentService studentService;

    @Autowired
    public TicketArchiveController(TicketArchiveService ticketArchiveService, StudentService studentService) {
        this.ticketArchiveService = ticketArchiveService;
        this.studentService = studentService;
    }

    @PostMapping
    public ResponseEntity<Void> archive(@PathVariable Long ticketId, Authentication authentication) {
        ticketArchiveService.archiveTicket(currentStudentId(authentication), ticketId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unarchive(@PathVariable Long ticketId, Authentication authentication) {
        ticketArchiveService.unarchiveTicket(currentStudentId(authentication), ticketId);
        return ResponseEntity.noContent().build();
    }

    private Long currentStudentId(Authentication authentication) {
        Student student = studentService.findByEmail(authentication.getName()).orElse(null);
        if (student == null) {
            throw new ResourceNotFoundException("Logged-in student not found");
        }
        return student.getId();
    }
}
