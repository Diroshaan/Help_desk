package com.helpdesk.ticketportal.controller;

import com.helpdesk.ticket.dto.TicketResponse;
import com.helpdesk.ticketportal.service.TicketArchiveService;
import com.helpdesk.ticketportal.support.CurrentStudentResolver;
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
    private final CurrentStudentResolver currentStudent;

    @Autowired
    public ArchivedTicketListController(TicketArchiveService ticketArchiveService, CurrentStudentResolver currentStudent) {
        this.ticketArchiveService = ticketArchiveService;
        this.currentStudent = currentStudent;
    }

    @GetMapping("/api/tickets/archived")
    public List<TicketResponse> findArchived(Authentication authentication) {
        return ticketArchiveService.archivedTickets(currentStudent.currentStudentId(authentication)).stream()
                .map(TicketResponse::from)
                .toList();
    }
}
