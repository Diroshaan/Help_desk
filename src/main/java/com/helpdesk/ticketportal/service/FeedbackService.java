package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticketportal.dto.FeedbackSummaryResponse;
import com.helpdesk.ticketportal.entity.Feedback;
import com.helpdesk.ticketportal.event.FeedbackSubmittedEvent;
import com.helpdesk.ticketportal.repository.FeedbackRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Student feedback (1-5 rating and comment) on their resolved tickets, plus per-category stats.
 */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;

    // Observer publisher: announces FeedbackSubmittedEvent without knowing who listens
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public FeedbackService(FeedbackRepository feedbackRepository, TicketRepository ticketRepository,
                           CategoryRepository categoryRepository, ApplicationEventPublisher eventPublisher) {
        this.feedbackRepository = feedbackRepository;
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
    }

    // Only for the student's own RESOLVED ticket, and only once per ticket.
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

        // Two submits at once can both pass the check above; the unique constraint
        // catches the second one and we turn it into the same 409.
        Feedback saved;
        try {
            saved = feedbackRepository.saveAndFlush(feedback);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("Feedback has already been submitted for this ticket");
        }

        // Observer: publish only for new feedback, so an edit doesn't alert the officer again
        eventPublisher.publishEvent(new FeedbackSubmittedEvent(ticketId, ticket.getSubject(), rating));
        return saved;
    }

    // status isn't re-checked, so existing feedback can still be corrected later
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

    public FeedbackSummaryResponse summaryByCategory(String category) {
        // Unknown category is a 400 rather than "0 ratings". Retired categories are
        // still allowed since they have old feedback.
        if (category == null || category.isBlank() || !categoryRepository.existsByName(category)) {
            throw new IllegalArgumentException("Unknown category: " + category);
        }

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

    // someone else's ticket is a 404, not a 403, so ids can't be probed
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
