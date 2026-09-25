package com.helpdesk.notification.listener;

import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.notification.event.PasswordChangedEvent;
import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import com.helpdesk.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * OBSERVER PATTERN: a second observer, for account security events.
 *
 * Kept separate from TicketStatusNotifier so each listener has one job. It
 * also shows the other half of the Observer idea: PasswordService and
 * QueueService publish completely different events, and neither knows the
 * notification module exists. Same AFTER_COMMIT / REQUIRES_NEW reasoning as
 * TicketStatusNotifier.
 */
@Component
public class AccountSecurityNotifier {

    private final AppUserRepository appUserRepository;
    private final NotificationService notificationService;

    public AccountSecurityNotifier(AppUserRepository appUserRepository,
                                   NotificationService notificationService) {
        this.appUserRepository = appUserRepository;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPasswordChanged(PasswordChangedEvent event) {
        appUserRepository.findById(event.userId()).ifPresent(user -> notificationService.notify(
                NotificationRecipient.from(user),
                new NotificationMessage("Your password was changed",
                        "Your UNIHELP password was just changed and your other devices were signed out. "
                                + "If this wasn't you, contact the help desk straight away.",
                        null)));
    }
}
