package com.helpdesk.ticketportal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * F3 - marks a resolved ticket as archived/hidden from a student's active
 * history view. This is a soft-delete: the underlying Ticket is untouched,
 * only its visibility in the student's default list is affected.
 */
@Entity
@Table(
    name = "archived_tickets",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "ticket_id"})
)
public class ArchivedTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Student ID is required")
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @NotNull(message = "Ticket ID is required")
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    private LocalDateTime archivedAt = LocalDateTime.now();

    public ArchivedTicket() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
