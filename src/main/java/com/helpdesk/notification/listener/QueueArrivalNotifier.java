package com.helpdesk.notification.listener;

import com.helpdesk.common.reference.repository.CategoryRepository;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.notification.event.TicketSubmittedEvent;
import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import com.helpdesk.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * OBSERVER PATTERN: a second concrete observer, this time for officers.
 *
 * F1 user story US-04: "As a help desk officer, I want to update my own
 * profile and notification preferences, so that I'm alerted through my
 * preferred channel when new tickets land in my queue." The preferences
 * (OfficerProfile page, Officer.email/portalNotificationsEnabled) existed; this
 * class is the "alerted when new tickets land" half.
 *
 * WHO IS TOLD
 * -----------
 * Every active, non-removed officer who serves the ticket's department. "The
 * ticket's department" is:
 *   1. the department the ticket was routed to, if it was routed at creation
 *      (F4-N5 will do that), otherwise
 *   2. the department that owns the ticket's category
 *      (Ticket.category -> Category.name -> Category.department).
 * A ticket whose category matches nothing tells nobody, and says so in the
 * log: guessing a desk would send officers work that is not theirs.
 *
 * HOW THEY ARE TOLD - STRATEGY, UNCHANGED
 * ---------------------------------------
 * NotificationService hands the message to every channel the officer has
 * switched on. An officer who turned portal alerts off gets no inbox entry,
 * with no code here to make that decision. That is the point of keeping
 * "what happened" (this class) apart from "how to tell someone" (the channels).
 *
 * WHEN - AFTER_COMMIT, in a new transaction
 * -----------------------------------------
 * Same choice and same reasons as TicketStatusNotifier: a ticket that fails to
 * save must not announce itself, and a failing notification must never undo a
 * student's submission. fallbackExecution = true makes it still run when the
 * publisher has no transaction (TicketService.createTicket has none until
 * F2-N2 adds @Transactional); the ticket is already committed then, so the
 * guarantee holds either way.
 */
@Component
public class QueueArrivalNotifier {

    private static final Logger log = LoggerFactory.getLogger(QueueArrivalNotifier.class);

    private final CategoryRepository categoryRepository;
    private final OfficerRepository officerRepository;
    private final NotificationService notificationService;

    public QueueArrivalNotifier(CategoryRepository categoryRepository,
                                OfficerRepository officerRepository,
                                NotificationService notificationService) {
        this.categoryRepository = categoryRepository;
        this.officerRepository = officerRepository;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketSubmitted(TicketSubmittedEvent event) {
        String departmentCode = departmentFor(event);
        if (departmentCode == null) {
            log.warn("New ticket {} has category '{}', which belongs to no department; no officer was alerted.",
                    event.ticketId(), event.categoryName());
            return;
        }

        List<Officer> officers = officerRepository.findActiveServingDepartment(departmentCode);
        NotificationMessage message = messageFor(event);
        for (Officer officer : officers) {
            notificationService.notify(NotificationRecipient.from(officer), message);
        }
    }

    private String departmentFor(TicketSubmittedEvent event) {
        if (event.assignedDepartmentCode() != null && !event.assignedDepartmentCode().isBlank()) {
            return event.assignedDepartmentCode();
        }
        if (event.categoryName() == null) {
            return null;
        }
        return categoryRepository.findByNameWithDepartment(event.categoryName())
                .map(category -> category.getDepartment().getCode())
                .orElse(null);
    }

    /** Package-visible so the wording can be unit-tested without a database. */
    static NotificationMessage messageFor(TicketSubmittedEvent event) {
        String subject = "\"" + event.subject() + "\"";
        String category = event.categoryName() == null ? "" : " (" + event.categoryName() + ")";
        return new NotificationMessage("New ticket in your queue",
                subject + category + " was just submitted and is waiting to be picked up.",
                // The officer's view of the ticket, not the student's.
                "#/queue/" + event.ticketId());
    }
}
