package com.helpdesk.ticketportal.event;

/** Observer event: published by FeedbackService when a student rates a resolved ticket. */
public record FeedbackSubmittedEvent(Long ticketId, String ticketSubject, int rating) {
}
