package com.helpdesk.ticketportal.service;

import com.helpdesk.common.user.entity.Officer;
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

    // Notification text is limited to 500 characters. A ticket subject is at most 150,
    // so a comment of up to 300 characters always fits.
    static final int MAX_COMMENT = 300;
    // Notification.body column limit
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
        // find the answer to this ticket; no answer means no one to notify
        Resolution resolution = resolutionRepository.findByTicketId(ticketId).orElse(null);
        if (resolution == null) {
            return;
        }

        // find the officer who wrote it; skip them if missing or inactive (they can't sign in)
        Officer officer = officerRepository.findById(resolution.getOfficerId()).orElse(null);
        if (officer == null || !officer.isActive()) {
            return;
        }

        //defines who gets the notification, the officer who answered the ticket
        NotificationRecipient recipient = NotificationRecipient.from(officer);

        //defines what the notification says, the subject, rating and comment
        NotificationMessage message = messageFor(ticketId, ticketSubject, rating, comment);

        //send it through the notification module (portal and email channels)
        notificationService.notify(recipient, message);
    }

    // Builds the notification text, shortening long comments to fit 500 characters
    static NotificationMessage messageFor(Long ticketId, String ticketSubject, int rating, String comment) {
        String body = "\"" + ticketSubject + "\" was rated " + rating + "/5.";
        if (comment != null && !comment.isBlank()) {
            String text = comment.strip();
            if (text.length() > MAX_COMMENT) {
                text = text.substring(0, MAX_COMMENT) + "\u2026";
            }
            body = body + " Comment: \"" + text + "\"";
        }
        // keeps the whole body within the column limit
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY - 1) + "\u2026";
        }
        return new NotificationMessage("A student rated your answer", body, "#/queue/" + ticketId);
    }
}
