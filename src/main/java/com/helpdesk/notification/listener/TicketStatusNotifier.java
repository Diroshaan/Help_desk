package com.helpdesk.notification.listener;

import com.helpdesk.notification.event.TicketStatusChangedEvent;
import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import com.helpdesk.notification.service.NotificationService;
import com.helpdesk.profile.repository.StudentRepository;
import com.helpdesk.ticket.entity.TicketStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * OBSERVER PATTERN: the observer.
 *
 * Reacts to ticket status changes by telling the student who owns the ticket.
 *
 * Two choices worth explaining:
 *
 * AFTER_COMMIT: the listener only runs once the officer's change has
 * actually been saved. If the status change fails and rolls back, the
 * student is never told about something that didn't happen.
 *
 * REQUIRES_NEW: after the commit the original transaction is finished, so
 * the portal channel needs a fresh one to save its Notification row. It
 * also means a problem while notifying can't undo the officer's work, which
 * is already committed. A missed notification is annoying. A lost status
 * change would be a real bug.
 *
 * fallbackExecution = true: if someone ever publishes this event outside a
 * transaction, run straight away instead of silently dropping it (Spring's
 * default).
 */
@Component
public class TicketStatusNotifier {

    private final StudentRepository studentRepository;
    private final NotificationService notificationService;

    public TicketStatusNotifier(StudentRepository studentRepository,
                                NotificationService notificationService) {
        this.studentRepository = studentRepository;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketStatusChanged(TicketStatusChangedEvent event) {
        studentRepository.findById(event.studentId())
                // A deactivated student can't sign in to read it, so don't write to their inbox.
                .filter(student -> student.isActive())
                .ifPresent(student -> notificationService.notify(
                        NotificationRecipient.from(student), messageFor(event)));
    }

    /** Package-private so the unit test can check the wording without Spring. */
    static NotificationMessage messageFor(TicketStatusChangedEvent event) {
        String subject = "\"" + event.subject() + "\"";
        String link = "#/tickets/" + event.ticketId();

        if (event.toStatus() == TicketStatus.IN_PROGRESS && event.fromStatus() == TicketStatus.RESOLVED) {
            return new NotificationMessage("Your ticket was reopened",
                    "The answer to " + subject + " was withdrawn, and an officer is working on it again.", link);
        }
        if (event.toStatus() == TicketStatus.IN_PROGRESS) {
            return new NotificationMessage("Your ticket is being worked on",
                    "A help desk officer has picked up " + subject + ".", link);
        }
        if (event.toStatus() == TicketStatus.RESOLVED) {
            return new NotificationMessage("Your ticket has been resolved",
                    "The help desk has responded to " + subject
                            + ". Open the ticket to see it and rate the answer.", link);
        }
        return new NotificationMessage("Ticket status updated",
                subject + " is now " + event.toStatus() + ".", link);
    }
}
