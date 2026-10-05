package com.helpdesk.ticket.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/**
 * A support ticket submitted by a student.
 * The assigned* fields are filled in by the queue; assignedDepartmentId is a
 * String because departments are keyed by their code.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Student ID is required")
    @Column(nullable = false)
    private Long studentId;

    @NotBlank(message = "Subject is required")
    @Column(nullable = false, length = 150)
    private String subject;

    @NotBlank(message = "Description is required")
    @Column(nullable = false, length = 2000)
    private String description;

    // Foreign key to categories.name, so the length matches that column.
    @NotBlank(message = "Category is required")
    @Column(nullable = false, length = 120)
    private String category;

    // Stored as VARCHAR, not a MySQL ENUM, so adding a value later doesn't
    // break inserts (ddl-auto=update never alters an existing column).
    @NotNull(message = "Priority is required")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    // Null until the queue assigns the ticket.
    private Long assignedOfficerId;

    private String assignedDepartmentId;

    private LocalDateTime assignedAt;

    // Null until the ticket reaches TicketStatus.RESOLVED.
    private LocalDateTime resolvedAt;

    // Optimistic locking: if a student and an officer save the same ticket at
    // once, the later save fails with a 409 instead of overwriting the other.
    @Version
    @ColumnDefault("0")
    private Long version;

    @PreUpdate
    public void touchUpdatedAt(){
        this.updatedAt = LocalDateTime.now();
    }

    public Ticket() {}

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getStudentId() {
        return studentId;
    }
    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }
    public String getSubject() {
        return subject;
    }
    public void setSubject(String subject) {
        this.subject = subject;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public TicketPriority getPriority() {
        return priority;
    }
    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }
    public TicketStatus getStatus() {
        return status;
    }
    public void setStatus(TicketStatus status) {
        this.status = status;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    public Long getAssignedOfficerId() {
        return assignedOfficerId;
    }
    public void setAssignedOfficerId(Long assignedOfficerId) {
        this.assignedOfficerId = assignedOfficerId;
    }
    public String getAssignedDepartmentId() {
        return assignedDepartmentId;
    }
    public void setAssignedDepartmentId(String assignedDepartmentId) {
        this.assignedDepartmentId = assignedDepartmentId;
    }
    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }
    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }
    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
    public Long getVersion() {
        return version;
    }
}
