package com.helpdesk.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * One message in a user's portal inbox, saved by PortalNotificationChannel.
 * recipientUserId is a plain id (not @ManyToOne) so listing the inbox doesn't load users.
 * readAt == null means unread.
 */
@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notification_recipient_created",
                columnList = "recipient_user_id, created_at"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String title;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String body;

    @Size(max = 200)
    @Column(length = 200)
    private String link;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() {
    }

    public Notification(Long recipientUserId, String title, String body, String link) {
        this.recipientUserId = recipientUserId;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
