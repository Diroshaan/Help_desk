package com.helpdesk.notification.service;

/**
 * What we want to tell someone, independent of how it gets to them.
 *
 * The same message goes to every channel the person has switched on. The
 * portal channel stores it and the email channel mails it, but neither one
 * changes the wording. Keeping the text here, rather than inside each
 * channel, is what stops the two versions drifting apart.
 *
 * @param title short line shown in bold in the portal list and used as the email subject
 * @param body  one or two sentences of detail
 * @param link  where the portal should take the user when they click it (a hash
 *              route such as "#/tickets/12"), or null when there is nowhere to go
 */
public record NotificationMessage(String title, String body, String link) {
}
