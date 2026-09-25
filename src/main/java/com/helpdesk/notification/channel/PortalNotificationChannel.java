package com.helpdesk.notification.channel;

import com.helpdesk.notification.entity.Notification;
import com.helpdesk.notification.repository.NotificationRepository;
import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import org.springframework.stereotype.Component;

/**
 * STRATEGY PATTERN: concrete strategy #1, the in-app inbox.
 *
 * Saves the message as a Notification row. The user sees it next time they
 * open the portal (GET /api/notifications). This is the "Portal Alerts"
 * toggle on the profile page.
 */
@Component
public class PortalNotificationChannel implements NotificationChannel {

    private final NotificationRepository notificationRepository;

    public PortalNotificationChannel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public String getName() {
        return "portal";
    }

    @Override
    public boolean isEnabledFor(NotificationRecipient recipient) {
        return recipient.portalEnabled();
    }

    @Override
    public void send(NotificationRecipient recipient, NotificationMessage message) {
        notificationRepository.save(new Notification(
                recipient.userId(), message.title(), message.body(), message.link()));
    }
}
