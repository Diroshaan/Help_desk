package com.helpdesk.notification.event;

/**
 * Observer event: published by PasswordService after a password change, so the user
 * hears about it (and can act if it wasn't them).
 */
public record PasswordChangedEvent(Long userId) {
}
