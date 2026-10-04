package com.helpdesk.notification.dto;

import com.helpdesk.notification.entity.Notification;

import java.time.LocalDateTime;

/** Inbox item. No recipient id since it's always the caller. */
public record NotificationResponse(Long id,
                                   String title,
                                   String body,
                                   String link,
                                   LocalDateTime createdAt,
                                   boolean read) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getBody(), n.getLink(),
                n.getCreatedAt(), n.isRead());
    }
}
