package com.helpdesk.ticketportal.dto;

import java.util.Map;

/**
 * Aggregate feedback stats, e.g. across a department or all tickets.
 * ratingBreakdown maps each star value (1-5) to how many feedback entries gave that rating.
 */
public class FeedbackSummaryResponse {

    private final double averageRating;
    private final long totalCount;
    private final Map<Integer, Long> ratingBreakdown;

    public FeedbackSummaryResponse(double averageRating, long totalCount, Map<Integer, Long> ratingBreakdown) {
        this.averageRating = averageRating;
        this.totalCount = totalCount;
        this.ratingBreakdown = ratingBreakdown;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public Map<Integer, Long> getRatingBreakdown() {
        return ratingBreakdown;
    }
}
