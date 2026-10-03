package com.helpdesk.ticketportal.event;

/**
 * OBSERVER PATTERN: the event F3 publishes.
 *
 * Published by FeedbackService after a student rates a resolved ticket.
 * Plain data, no behaviour: it says what happened, never who should react -
 * that is each observer's decision (see FeedbackReceivedNotifier).
 */
public record FeedbackSubmittedEvent(Long ticketId, String ticketSubject, int rating) {
}
