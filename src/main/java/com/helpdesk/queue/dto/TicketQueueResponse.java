package com.helpdesk.queue.dto;

import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;

import java.time.LocalDateTime;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * The officer-facing view of a ticket. Distinct from F2's
 * ticket.dto.TicketResponse (shaped for a student viewing their own
 * ticket) because it additionally exposes the queue-routing fields an
 * officer needs - assignedOfficerId, assignedDepartmentId, assignedAt,
 * resolvedAt - none of which a student has any business seeing or which
 * TicketResponse currently returns.
 */
public class TicketQueueResponse {

    private final Long id;
    private final Long studentId;
    private final String subject;
    private final String description;
    private final String category;
    private final TicketPriority priority;
    private final TicketStatus status;
    private final Long assignedOfficerId;
    private final String assignedDepartmentId;
    private final LocalDateTime assignedAt;
    private final LocalDateTime resolvedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public TicketQueueResponse(Long id, Long studentId, String subject, String description, String category,
                                TicketPriority priority, TicketStatus status, Long assignedOfficerId,
                                String assignedDepartmentId, LocalDateTime assignedAt, LocalDateTime resolvedAt,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.studentId = studentId;
        this.subject = subject;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.status = status;
        this.assignedOfficerId = assignedOfficerId;
        this.assignedDepartmentId = assignedDepartmentId;
        this.assignedAt = assignedAt;
        this.resolvedAt = resolvedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TicketQueueResponse from(Ticket ticket) {
        return new TicketQueueResponse(ticket.getId(), ticket.getStudentId(), ticket.getSubject(),
                ticket.getDescription(), ticket.getCategory(), ticket.getPriority(), ticket.getStatus(),
                ticket.getAssignedOfficerId(), ticket.getAssignedDepartmentId(), ticket.getAssignedAt(),
                ticket.getResolvedAt(), ticket.getCreatedAt(), ticket.getUpdatedAt());
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
    public Long getAssignedOfficerId() {
        return assignedOfficerId;
    }
    public String getAssignedDepartmentId() {
        return assignedDepartmentId;
    }
    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }
    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
