package com.helpdesk.notification.channel;

import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * STRATEGY PATTERN: concrete strategy #2, email.
 *
 * To be upfront about it: this doesn't send real email yet. The project has
 * no mail server and no SMTP account, and we're not putting a Gmail password
 * into a public repository to get one. So for now it writes a single log line
 * saying what it would have sent, with the address masked so the log doesn't
 * collect everyone's email.
 *
 * That's still enough to show the pattern working. The student's email toggle
 * decides whether this strategy runs at all, and the log proves it ran. When
 * a mail server exists, only the body of send() changes (inject Spring's
 * JavaMailSender and call it). NotificationService, the listeners and the
 * other channel don't change. That's the point of keeping each channel
 * behind the interface.
 */
@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    @Override
    public String getName() {
        return "email";
    }

    @Override
    public boolean isEnabledFor(NotificationRecipient recipient) {
        return recipient.emailEnabled() && recipient.email() != null;
    }

    @Override
    public void send(NotificationRecipient recipient, NotificationMessage message) {
        log.info("[email] to {} - subject: \"{}\" (no mail server configured, so this is logged instead of sent)",
                mask(recipient.email()), message.title());
    }

    /** "nimal.test@my.sliit.lk" becomes "n***@my.sliit.lk". Enough to debug with, not enough to harvest. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
