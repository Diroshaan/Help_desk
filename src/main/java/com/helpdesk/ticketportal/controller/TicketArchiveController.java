package com.helpdesk.ticketportal.controller;

import com.helpdesk.ticketportal.service.TicketArchiveService;
import com.helpdesk.ticketportal.support.CurrentStudentResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Archive and unarchive one of the student's own tickets. */
@RestController
@RequestMapping("/api/tickets/{ticketId}/archive")
public class TicketArchiveController {

    private final TicketArchiveService ticketArchiveService;
    private final CurrentStudentResolver currentStudent;

    @Autowired
    public TicketArchiveController(TicketArchiveService ticketArchiveService, CurrentStudentResolver currentStudent) {
        this.ticketArchiveService = ticketArchiveService;
        this.currentStudent = currentStudent;
    }

    @PostMapping
    public ResponseEntity<Void> archive(@PathVariable Long ticketId, Authentication authentication) {
        ticketArchiveService.archiveTicket(currentStudent.currentStudentId(authentication), ticketId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unarchive(@PathVariable Long ticketId, Authentication authentication) {
        ticketArchiveService.unarchiveTicket(currentStudent.currentStudentId(authentication), ticketId);
        return ResponseEntity.noContent().build();
    }
}
