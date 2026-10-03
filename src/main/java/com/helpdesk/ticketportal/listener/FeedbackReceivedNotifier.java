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
 * OBSERVER PATTERN: an observer of F3's FeedbackSubmittedEvent.
 *
 * Tells the officer who wrote a ticket's answer that the student has rated
 * it. FeedbackService never calls this class - it only publishes the event -
 * so another reaction (e.g. flagging 1/5 ratings to a supervisor) would be a
 * new listener, with no change to FeedbackService.
 *
 * The notification itself goes through NotificationService, whose channels
 * (portal, email) are the STRATEGY pattern: the officer's own preferences
 * choose which ones run.
 *
 * Same transaction choices as notification/listener/TicketStatusNotifier:
 *
 * AFTER_COMMIT: only runs once the feedback is really saved, so an officer is
 * never told "you were rated" about feedback that rolled back.
 *
 * REQUIRES_NEW: the notification row is saved in its own transaction, so a
 * problem while notifying can't undo the student's feedback.
 *
 * fallbackExecution = true: FeedbackService.submitFeedback isn't itself
 * transactional (its saveAndFlush commits on its own), so the event is
 * published outside a transaction; without this Spring would silently drop it.
 *
 * Reads F4's ResolutionRepository read-only, as F3 already does under
 * contract C5, to find which officer wrote the answer.
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
                // A suspended or removed officer can't sign in to read it, so don't write to their inbox.
                .filter(officer -> officer.isActive())
                .ifPresent(officer -> notificationService.notify(
                        NotificationRecipient.from(officer), messageFor(event)));
    }

    /** Package-visible so the wording can be checked without Spring. */
    static NotificationMessage messageFor(FeedbackSubmittedEvent event) {
        return new NotificationMessage("A student rated your answer",
                "\"" + event.ticketSubject() + "\" was rated " + event.rating() + "/5.",
                // The officer's view of the ticket, not the student's.
                "#/queue/" + event.ticketId());
    }
}
