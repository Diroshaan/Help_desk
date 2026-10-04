package com.helpdesk.notification.channel;

import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Strategy: delivers a notification by email.
 * We have no mail server, so for now it only logs what it would send (address masked).
 * Adding real email later only changes send().
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

    /** "nimal.test@my.sliit.lk" becomes "n***@my.sliit.lk". */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
