package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticketportal.dto.FeedbackSummaryResponse;
import com.helpdesk.ticketportal.entity.Feedback;
import com.helpdesk.ticketportal.repository.FeedbackRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final TicketRepository ticketRepository;

    @Autowired
    public FeedbackService(FeedbackRepository feedbackRepository, TicketRepository ticketRepository) {
        this.feedbackRepository = feedbackRepository;
        this.ticketRepository = ticketRepository;
    }

    // Submitting requires: the ticket exists and belongs to this student,
    // the ticket is RESOLVED (feedback only makes sense once support has
    // actually responded), and no feedback exists yet for it.
    public Feedback submitFeedback(Long studentId, Long ticketId, Integer rating, String comment) {
        Ticket ticket = findOwnedTicket(ticketId, studentId);

        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new ValidationException("Feedback can only be submitted for resolved tickets");
        }
        if (feedbackRepository.existsByTicketId(ticketId)) {
            throw new DuplicateResourceException("Feedback has already been submitted for this ticket");
        }

        Feedback feedback = new Feedback();
        feedback.setStudentId(studentId);
        feedback.setTicketId(ticketId);
        feedback.setRating(rating);
        feedback.setComment(comment);

        return feedbackRepository.save(feedback);
    }

    // Updating only requires ownership - unlike submission, the ticket's
    // status is not re-checked. Once feedback exists it can be corrected
    // even if the ticket has since moved on (e.g. reopened).
    public Feedback updateFeedback(Long ticketId, Long studentId, Integer rating, String comment) {
        findOwnedTicket(ticketId, studentId);
        Feedback feedback = findByTicketId(ticketId);

        feedback.setRating(rating);
        feedback.setComment(comment);
        return feedbackRepository.save(feedback);
    }

    public Feedback getByTicketId(Long ticketId, Long studentId) {
        findOwnedTicket(ticketId, studentId);
        return findByTicketId(ticketId);
    }

    // Aggregate stats across every ticket in a category: average rating,
    // how many feedback entries exist, and a per-star (1-5) breakdown.
    public FeedbackSummaryResponse summaryByCategory(String category) {
        List<Long> ticketIds = ticketRepository.findByCategory(category).stream()
                .map(Ticket::getId)
                .toList();

        List<Feedback> feedbackEntries = ticketIds.isEmpty()
                ? Collections.emptyList()
                : feedbackRepository.findByTicketIdIn(ticketIds);

        long totalCount = feedbackEntries.size();
        double averageRating = feedbackEntries.stream()
                .mapToInt(Feedback::getRating)
                .average()
                .orElse(0.0);

        Map<Integer, Long> ratingBreakdown = new TreeMap<>(feedbackEntries.stream()
                .collect(Collectors.groupingBy(Feedback::getRating, Collectors.counting())));
        for (int star = 1; star <= 5; star++) {
            ratingBreakdown.putIfAbsent(star, 0L);
        }

        return new FeedbackSummaryResponse(averageRating, totalCount, ratingBreakdown);
    }

    private Ticket findOwnedTicket(Long ticketId, Long studentId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (!ticket.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Ticket not found");
        }
        return ticket;
    }

    private Feedback findByTicketId(Long ticketId) {
        return feedbackRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));
    }
}
