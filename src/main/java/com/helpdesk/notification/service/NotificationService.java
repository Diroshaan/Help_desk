package com.helpdesk.notification.service;

import com.helpdesk.notification.channel.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * STRATEGY PATTERN: the context.
 *
 * Spring hands this class every bean that implements NotificationChannel
 * (right now PortalNotificationChannel and EmailNotificationChannel). For each
 * message it asks every channel "is this person subscribed to you?" and, if
 * so, "deliver it". It never checks which class it's talking to.
 *
 * Compare this with the lab's Student class: Student holds a
 * CompetitionStrategy and calls getPoints() without knowing whether it's
 * CodeFest or RoboFest. Here the strategy is chosen per user from their
 * notification preferences, and a user can have more than one switched on,
 * which is why this holds a list rather than a single field.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<NotificationChannel> channels;

    public NotificationService(List<NotificationChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    /**
     * Sends the message through every channel the recipient has turned on.
     *
     * One broken channel mustn't stop the others. If email fails, the portal
     * message should still arrive, so each send is wrapped on its own.
     *
     * @return the names of the channels that delivered it (tests use this;
     *         an empty list means the user has everything switched off)
     */
    public List<String> notify(NotificationRecipient recipient, NotificationMessage message) {
        List<String> delivered = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabledFor(recipient)) {
                continue;
            }
            try {
                channel.send(recipient, message);
                delivered.add(channel.getName());
            } catch (RuntimeException e) {
                log.warn("Notification channel '{}' failed for user {}: {}",
                        channel.getName(), recipient.userId(), e.getMessage());
            }
        }
        return delivered;
    }
}
