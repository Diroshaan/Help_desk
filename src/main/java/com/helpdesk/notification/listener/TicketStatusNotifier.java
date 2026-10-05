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
 * Observer: when a ticket's status changes, tell the student who owns it.
 * Runs after commit so we never announce a change that was rolled back, and in its
 * own transaction so a failed notification can't undo the officer's update.
 * fallbackExecution still runs it if the event is published outside a transaction.
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
                // deactivated students can't sign in, so skip them
                .filter(student -> student.isActive())
                .ifPresent(student -> notificationService.notify(
                        NotificationRecipient.from(student), messageFor(event)));
    }

    /** Package-private so the wording can be unit tested. */
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
