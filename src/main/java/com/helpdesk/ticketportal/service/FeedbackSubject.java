package com.helpdesk.ticketportal.service;

/**
 * Subject interface.
 * Something other classes can subscribe to, to hear about new feedback.
 */
public interface FeedbackSubject {

    void addObserver(FeedbackObserver observer);

    void removeObserver(FeedbackObserver observer);

    void notifyObservers(Long ticketId, String ticketSubject, int rating, String comment);
}
