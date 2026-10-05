package com.helpdesk.ticketportal.dto;

import java.util.Map;

/** Aggregate feedback stats. ratingBreakdown maps each star value (1-5) to its count. */
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
