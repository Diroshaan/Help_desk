package com.helpdesk.ticketportal.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.ticketportal.dto.FeedbackRequest;
import com.helpdesk.ticketportal.dto.FeedbackResponse;
import com.helpdesk.ticketportal.dto.FeedbackSummaryResponse;
import com.helpdesk.ticketportal.entity.Feedback;
import com.helpdesk.ticketportal.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final StudentService studentService;

    @Autowired
    public FeedbackController(FeedbackService feedbackService, StudentService studentService) {
        this.feedbackService = feedbackService;
        this.studentService = studentService;
    }

    @PostMapping("/api/tickets/{ticketId}/feedback")
    public ResponseEntity<FeedbackResponse> submit(@PathVariable Long ticketId,
                                                     @Valid @RequestBody FeedbackRequest request,
                                                     Authentication authentication) {
        Feedback feedback = feedbackService.submitFeedback(
                currentStudentId(authentication), ticketId, request.getRating(), request.getComment());
        return ResponseEntity.status(HttpStatus.CREATED).body(FeedbackResponse.from(feedback));
    }

    @PutMapping("/api/tickets/{ticketId}/feedback")
    public ResponseEntity<FeedbackResponse> update(@PathVariable Long ticketId,
                                                     @Valid @RequestBody FeedbackRequest request,
                                                     Authentication authentication) {
        Feedback feedback = feedbackService.updateFeedback(
                ticketId, currentStudentId(authentication), request.getRating(), request.getComment());
        return ResponseEntity.ok(FeedbackResponse.from(feedback));
    }

    @GetMapping("/api/tickets/{ticketId}/feedback")
    public FeedbackResponse getByTicket(@PathVariable Long ticketId, Authentication authentication) {
        Feedback feedback = feedbackService.getByTicketId(ticketId, currentStudentId(authentication));
        return FeedbackResponse.from(feedback);
    }

    @GetMapping("/api/feedback/summary")
    public FeedbackSummaryResponse summary(@RequestParam String category) {
        return feedbackService.summaryByCategory(category);
    }

    private Long currentStudentId(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in student not found"));
    }
}
