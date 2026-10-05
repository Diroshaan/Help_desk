package com.helpdesk.ticketportal.service;

import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import com.helpdesk.notification.service.NotificationService;
import com.helpdesk.queue.entity.Resolution;
import com.helpdesk.queue.repository.ResolutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete Observer: when new feedback is submitted, tells the officer
 * who answered the ticket that it was rated.
 * Implements observer interface
 */
@Component
public class FeedbackReceivedNotifier implements FeedbackObserver {

    // Notification.body is limited to 500 characters, so long comments are shortened
    static final int MAX_BODY = 500;

    private final ResolutionRepository resolutionRepository;
    private final OfficerRepository officerRepository;
    private final NotificationService notificationService;

    // Spring injects the repositories and NotificationService
    public FeedbackReceivedNotifier(ResolutionRepository resolutionRepository,
                                    OfficerRepository officerRepository,
                                    NotificationService notificationService) {
        this.resolutionRepository = resolutionRepository;
        this.officerRepository = officerRepository;
        this.notificationService = notificationService;
    }

    // Observer update: notifies the officer who answered the ticket that it was rated
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(Long ticketId, String ticketSubject, int rating, String comment) {
        resolutionRepository.findByTicketId(ticketId)
                .map(Resolution::getOfficerId)
                .flatMap(officerRepository::findById)
                // inactive officers can't sign in, so skip them
                .filter(officer -> officer.isActive())
                .ifPresent(officer -> notificationService.notify(
                        NotificationRecipient.from(officer), messageFor(ticketId, ticketSubject, rating, comment)));
    }

    // Builds the notification text, shortening long comments to fit 500 characters
    static NotificationMessage messageFor(Long ticketId, String ticketSubject, int rating, String comment) {
        String body = "\"" + ticketSubject + "\" was rated " + rating + "/5.";
        if (comment != null && !comment.isBlank()) {
            String prefix = body + " Comment: \"";
            int room = MAX_BODY - prefix.length() - 1; // leave space for the closing quote
            String text = comment.strip();
            if (text.length() > room) {
                text = text.substring(0, Math.max(0, room - 1)) + "\u2026";
            }
            body = prefix + text + "\"";
        }
        return new NotificationMessage("A student rated your answer", body, "#/queue/" + ticketId);
    }
}
