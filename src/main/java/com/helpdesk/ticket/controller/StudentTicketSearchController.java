package com.helpdesk.ticket.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.ticket.dto.TicketResponse;
import com.helpdesk.ticket.dto.TicketSearchCriteria;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.service.StudentTicketQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * F3 - exposes StudentTicketQueryService over HTTP. Kept as its own
 * controller (rather than a method on TicketController) so it doesn't
 * collide with F2's ticket CRUD endpoints in that file.
 */
@RestController
@RequestMapping("/api/tickets")
public class StudentTicketSearchController {

    private final StudentTicketQueryService studentTicketQueryService;
    private final StudentService studentService;

    @Autowired
    public StudentTicketSearchController(StudentTicketQueryService studentTicketQueryService,
                                          StudentService studentService) {
        this.studentTicketQueryService = studentTicketQueryService;
        this.studentService = studentService;
    }

    @GetMapping("/search")
    public Page<TicketResponse> search(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus(status);
        criteria.setPriority(priority);
        criteria.setCategory(category);
        criteria.setKeyword(keyword);
        criteria.setSortBy(sortBy);
        criteria.setSortDirection(sortDirection);
        criteria.setPage(page);
        criteria.setSize(size);

        Page<Ticket> results = studentTicketQueryService.search(currentStudentId(authentication), criteria);
        return results.map(TicketResponse::from);
    }

    private Long currentStudentId(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in student not found"));
    }
}
