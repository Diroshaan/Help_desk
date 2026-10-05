package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;

import java.time.LocalDateTime;

public class TicketResponse {

    private final Long id;
    private final Long studentId;
    private final String subject;
    private final String description;
    private final String category;
    private final TicketPriority priority;
    private final TicketStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public TicketResponse(Long id, Long studentId, String subject, String description, String category,
                           TicketPriority priority, TicketStatus status, LocalDateTime createdAt,
                           LocalDateTime updatedAt) {
        this.id = id;
        this.studentId = studentId;
        this.subject = subject;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getStudentId(), ticket.getSubject(),
                ticket.getDescription(), ticket.getCategory(), ticket.getPriority(), ticket.getStatus(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    public Long getId() {
        return id;
    }
    public Long getStudentId() {
        return studentId;
    }
    public String getSubject() {
        return subject;
    }
    public String getDescription() {
        return description;
    }
    public String getCategory() {
        return category;
    }
    public TicketPriority getPriority() {
        return priority;
    }
    public TicketStatus getStatus() {
        return status;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
