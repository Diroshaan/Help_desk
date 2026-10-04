package com.helpdesk.ticketportal.listener;

import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import com.helpdesk.notification.service.NotificationService;
import com.helpdesk.queue.entity.Resolution;
import com.helpdesk.queue.repository.ResolutionRepository;
import com.helpdesk.ticketportal.event.FeedbackSubmittedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: on FeedbackSubmittedEvent, tells the officer who answered the ticket that it was rated.
 * AFTER_COMMIT so we never report feedback that rolled back, and REQUIRES_NEW so a
 * failed notification can't undo the feedback. fallbackExecution because the event
 * is published outside a transaction.
 */
@Component
public class FeedbackReceivedNotifier {

    private final ResolutionRepository resolutionRepository;
    private final OfficerRepository officerRepository;
    private final NotificationService notificationService;

    public FeedbackReceivedNotifier(ResolutionRepository resolutionRepository,
                                    OfficerRepository officerRepository,
                                    NotificationService notificationService) {
        this.resolutionRepository = resolutionRepository;
        this.officerRepository = officerRepository;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFeedbackSubmitted(FeedbackSubmittedEvent event) {
        resolutionRepository.findByTicketId(event.ticketId())
                .map(Resolution::getOfficerId)
                .flatMap(officerRepository::findById)
                // inactive officers can't sign in, so skip them
                .filter(officer -> officer.isActive())
                .ifPresent(officer -> notificationService.notify(
                        NotificationRecipient.from(officer), messageFor(event)));
    }

    static NotificationMessage messageFor(FeedbackSubmittedEvent event) {
        return new NotificationMessage("A student rated your answer",
                "\"" + event.ticketSubject() + "\" was rated " + event.rating() + "/5.",
                "#/queue/" + event.ticketId());
    }
}
