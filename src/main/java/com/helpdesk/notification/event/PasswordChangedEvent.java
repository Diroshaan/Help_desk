package com.helpdesk.notification.event;

/**
 * OBSERVER PATTERN: published by PasswordService after a successful password change.
 *
 * The user is told on every channel they have on. If they didn't make the
 * change themselves, this message is how they find out.
 */
public record PasswordChangedEvent(Long userId) {
}
