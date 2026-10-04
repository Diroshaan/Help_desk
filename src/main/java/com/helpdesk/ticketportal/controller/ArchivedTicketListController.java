package com.helpdesk.ticketportal.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.ticket.dto.TicketResponse;
import com.helpdesk.ticketportal.service.TicketArchiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lists the student's archived tickets. Kept separate from TicketArchiveController
 * because of its base path; Spring matches "archived" before {ticketId}, so no clash.
 */
@RestController
public class ArchivedTicketListController {

    private final TicketArchiveService ticketArchiveService;
    private final StudentService studentService;

    @Autowired
    public ArchivedTicketListController(TicketArchiveService ticketArchiveService, StudentService studentService) {
        this.ticketArchiveService = ticketArchiveService;
        this.studentService = studentService;
    }

    @GetMapping("/api/tickets/archived")
    public List<TicketResponse> findArchived(Authentication authentication) {
        return ticketArchiveService.archivedTickets(currentStudentId(authentication)).stream()
                .map(TicketResponse::from)
                .toList();
    }

    private Long currentStudentId(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in student not found"));
    }
}
