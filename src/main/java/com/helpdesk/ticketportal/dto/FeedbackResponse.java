package com.helpdesk.ticketportal.dto;

import com.helpdesk.ticketportal.entity.Feedback;

import java.time.LocalDateTime;

public class FeedbackResponse {

    private final Long id;
    private final Long ticketId;
    private final Integer rating;
    private final String comment;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public FeedbackResponse(Long id, Long ticketId, Integer rating, String comment,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getTicketId(), feedback.getRating(),
                feedback.getComment(), feedback.getCreatedAt(), feedback.getUpdatedAt());
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Integer getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
