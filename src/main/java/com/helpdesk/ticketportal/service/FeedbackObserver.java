package com.helpdesk.ticketportal.service;

/**
 * Observer interface.
 * Ensure all observers receive the same updates
 */
public interface FeedbackObserver {

    // Called by the subject after a student submits new feedback on a ticket.
    void update(Long ticketId, String ticketSubject, int rating, String comment);
}
