package com.helpdesk.notification.service;

/**
 * What to tell someone, the same for every channel.
 * link is a portal hash route such as "#/tickets/12", or null.
 */
public record NotificationMessage(String title, String body, String link) {
}
