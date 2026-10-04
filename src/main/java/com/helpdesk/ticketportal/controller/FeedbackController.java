package com.helpdesk.ticketportal.controller;

import com.helpdesk.ticketportal.dto.FeedbackRequest;
import com.helpdesk.ticketportal.dto.FeedbackResponse;
import com.helpdesk.ticketportal.dto.FeedbackSummaryResponse;
import com.helpdesk.ticketportal.entity.Feedback;
import com.helpdesk.ticketportal.service.FeedbackService;
import com.helpdesk.ticketportal.support.CurrentStudentResolver;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Student feedback on their own tickets, plus the per-category summary. */
@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final CurrentStudentResolver currentStudent;

    @Autowired
    public FeedbackController(FeedbackService feedbackService, CurrentStudentResolver currentStudent) {
        this.feedbackService = feedbackService;
        this.currentStudent = currentStudent;
    }

    @PostMapping("/api/tickets/{ticketId}/feedback")
    public ResponseEntity<FeedbackResponse> submit(@PathVariable Long ticketId,
                                                     @Valid @RequestBody FeedbackRequest request,
                                                     Authentication authentication) {
        Feedback feedback = feedbackService.submitFeedback(
                currentStudent.currentStudentId(authentication), ticketId, request.getRating(), request.getComment());
        return ResponseEntity.status(HttpStatus.CREATED).body(FeedbackResponse.from(feedback));
    }

    @PutMapping("/api/tickets/{ticketId}/feedback")
    public ResponseEntity<FeedbackResponse> update(@PathVariable Long ticketId,
                                                     @Valid @RequestBody FeedbackRequest request,
                                                     Authentication authentication) {
        Feedback feedback = feedbackService.updateFeedback(
                ticketId, currentStudent.currentStudentId(authentication), request.getRating(), request.getComment());
        return ResponseEntity.ok(FeedbackResponse.from(feedback));
    }

    @GetMapping("/api/tickets/{ticketId}/feedback")
    public FeedbackResponse getByTicket(@PathVariable Long ticketId, Authentication authentication) {
        Feedback feedback = feedbackService.getByTicketId(ticketId, currentStudent.currentStudentId(authentication));
        return FeedbackResponse.from(feedback);
    }

    // officers and admins only (see SecurityConfig)
    @GetMapping("/api/feedback/summary")
    public FeedbackSummaryResponse summary(@RequestParam String category) {
        return feedbackService.summaryByCategory(category);
    }
}
