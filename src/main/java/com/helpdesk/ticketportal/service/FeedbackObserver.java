package com.helpdesk.ticketportal.service;

/**
 * Observer pattern - Observer interface.
 * Any class that wants to react to new feedback implements this.
 */
public interface FeedbackObserver {

    /** Called by the subject after a student submits new feedback on a ticket. */
    void update(Long ticketId, String ticketSubject, int rating, String comment);
}
