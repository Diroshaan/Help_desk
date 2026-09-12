package com.helpdesk.queue.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * A department that tickets get routed to and officers belong to (e.g.
 * "IT Support", "Registrar", "Finance"). Referenced by plain Long id from
 * both Officer.departmentId and Ticket.assignedDepartmentId, matching the
 * decoupled-reference pattern already used across this codebase (see
 * Ticket.studentId / Attachment.ticketId) instead of a JPA @ManyToOne.
 */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Department name is required")
    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    private LocalDateTime createdAt = LocalDateTime.now();

    //Constructors
    public Department() {}    // Required no-argument constructor for JPA

    //Getters and setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
