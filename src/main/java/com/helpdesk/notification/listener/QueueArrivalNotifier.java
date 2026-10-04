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
 * Observer: listens for TicketSubmittedEvent and alerts every active officer serving
 * the ticket's department (the routed department, else the category's department).
 * If the category matches no department we only log it rather than guess a desk.
 * Runs after commit in its own transaction, like TicketStatusNotifier.
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

    /** Package-private so the wording can be unit tested. */
    static NotificationMessage messageFor(TicketSubmittedEvent event) {
        String subject = "\"" + event.subject() + "\"";
        String category = event.categoryName() == null ? "" : " (" + event.categoryName() + ")";
        return new NotificationMessage("New ticket in your queue",
                subject + category + " was just submitted and is waiting to be picked up.",
                // officer's view of the ticket
                "#/queue/" + event.ticketId());
    }
}
