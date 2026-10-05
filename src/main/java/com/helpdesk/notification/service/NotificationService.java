package com.helpdesk.notification.service;

import com.helpdesk.notification.channel.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy context: Spring injects every NotificationChannel and we send through
 * each one the recipient has switched on, without checking the concrete class.
 * A user can have several channels on, so this holds a list.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<NotificationChannel> channels;

    public NotificationService(List<NotificationChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    /**
     * Each send is caught separately so one failing channel doesn't stop the others.
     * Returns the names of the channels that delivered it.
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
