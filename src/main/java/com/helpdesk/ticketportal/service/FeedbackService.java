package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticketportal.dto.FeedbackSummaryResponse;
import com.helpdesk.ticketportal.entity.Feedback;
import com.helpdesk.ticketportal.repository.FeedbackRepository;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Student feedback (1-5 rating and comment) on their resolved tickets, plus per-category stats.
 * Observer pattern - Concrete Subject: tells its observers when new feedback is submitted.
 */
@Service
public class FeedbackService implements FeedbackSubject {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private static final String ALREADY_SUBMITTED = "Feedback has already been submitted for this ticket";

    private final FeedbackRepository feedbackRepository;
    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final TicketService ticketService;

    // Observer pattern: the subscribed observers. CopyOnWriteArrayList so add/remove
    private final List<FeedbackObserver> observers = new CopyOnWriteArrayList<>();

    @Autowired
    public FeedbackService(FeedbackRepository feedbackRepository, TicketRepository ticketRepository,
                           CategoryRepository categoryRepository, TicketService ticketService,
                           List<FeedbackObserver> initialObservers) {
        this.feedbackRepository = feedbackRepository;
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.ticketService = ticketService;
        // Spring passes in every FeedbackObserver bean, so each one is attached at startup
        for (FeedbackObserver observer : initialObservers) {
            addObserver(observer);
        }
    }


    // Subscribes an observer, skipping duplicates
    @Override
    public void addObserver(FeedbackObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    // Unsubscribes an observer
    @Override
    public void removeObserver(FeedbackObserver observer) {
        observers.remove(observer);
    }

    // Calls update() on every subscribed observer with the new feedback details
    @Override
    public void notifyObservers(Long ticketId, String ticketSubject, int rating, String comment) {
        for (FeedbackObserver observer : observers) {
            // One failing observer must not stop the others or fail the student's request:
            // the feedback is already saved at this point.
            try {
                observer.update(ticketId, ticketSubject, rating, comment);
            } catch (RuntimeException e) {
                log.warn("Feedback observer {} failed for ticket {}",
                        observer.getClass().getSimpleName(), ticketId, e);
            }
        }
    }

    // Only for the student's own RESOLVED ticket, and only once per ticket.
    public Feedback submitFeedback(Long studentId, Long ticketId, Integer rating, String comment) {
        Ticket ticket = ticketService.getOwnedTicket(ticketId, studentId);

        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw new ValidationException("Feedback can only be submitted for resolved tickets");
        }
        if (feedbackRepository.existsByTicketId(ticketId)) {
            throw new DuplicateResourceException(ALREADY_SUBMITTED);
        }

        Feedback feedback = new Feedback();
        feedback.setStudentId(studentId);
        feedback.setTicketId(ticketId);
        feedback.setRating(rating);
        feedback.setComment(comment);


        Feedback saved;
        try {
            saved = feedbackRepository.saveAndFlush(feedback);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(ALREADY_SUBMITTED);
        }

        notifyObservers(ticketId, ticket.getSubject(), rating, comment);
        return saved;
    }

    // status isn't re-checked, so existing feedback can still be corrected later
    public Feedback updateFeedback(Long ticketId, Long studentId, Integer rating, String comment) {
        ticketService.getOwnedTicket(ticketId, studentId);
        Feedback feedback = findByTicketId(ticketId);

        feedback.setRating(rating);
        feedback.setComment(comment);
        return feedbackRepository.save(feedback);
    }

    // Returns the student's feedback for one of their tickets
    public Feedback getByTicketId(Long ticketId, Long studentId) {
        ticketService.getOwnedTicket(ticketId, studentId);
        return findByTicketId(ticketId);
    }

    // Calculates rating statistics for all tickets in a single category
    public FeedbackSummaryResponse summaryByCategory(String category) {

        if (category == null || category.isBlank() || !categoryRepository.existsByName(category)) {
            throw new IllegalArgumentException("Unknown category: " + category);
        }

        // ids of every ticket in this category
        List<Long> ticketIds = new ArrayList<>();
        for (Ticket ticket : ticketRepository.findByCategory(category)) {
            ticketIds.add(ticket.getId());
        }

        // the feedback on those tickets (none if the category has no tickets)
        List<Feedback> feedbackEntries = new ArrayList<>();
        if (!ticketIds.isEmpty()) {
            feedbackEntries = feedbackRepository.findByTicketIdIn(ticketIds);
        }

        // count the ratings, add them up, and count how many of each star (index 1-5)
        long totalCount = 0;
        long ratingSum = 0;
        long[] starCounts = new long[6];
        for (Feedback feedback : feedbackEntries) {
            totalCount++;
            ratingSum += feedback.getRating();
            starCounts[feedback.getRating()]++;
        }

        double averageRating = 0.0;
        if (totalCount > 0) {
            averageRating = (double) ratingSum / totalCount;
        }

        // every star from 1 to 5, in order, even if its count is 0
        Map<Integer, Long> ratingBreakdown = new TreeMap<>();
        for (int star = 1; star <= 5; star++) {
            ratingBreakdown.put(star, starCounts[star]);
        }

        return new FeedbackSummaryResponse(averageRating, totalCount, ratingBreakdown);
    }

    // Loads a ticket's feedback, 404 if there is none
    private Feedback findByTicketId(Long ticketId) {
        Feedback feedback = feedbackRepository.findByTicketId(ticketId).orElse(null);
        if (feedback == null) {
            throw new ResourceNotFoundException("Feedback not found");
        }
        return feedback;
    }
}
