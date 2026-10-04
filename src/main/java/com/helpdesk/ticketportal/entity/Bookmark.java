package com.helpdesk.ticketportal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** A student's bookmark on one of their tickets. */
@Entity
// one bookmark per (student, ticket), enforced by the DB since the service check can be raced
@Table(
    name = "bookmarks",
    uniqueConstraints = @UniqueConstraint(name = "uq_bookmark_student_ticket", columnNames = {"student_id", "ticket_id"})
)
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Ticket ID is required")
    @Column(nullable = false)
    private Long ticketId;

    @NotNull(message = "Student ID is required")
    @Column(nullable = false)
    private Long studentId;

    private Long folderId;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Bookmark() {}

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getTicketId() {
        return ticketId;
    }
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    public Long getStudentId() {
        return studentId;
    }
    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }
    public Long getFolderId() {
        return folderId;
    }
    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
