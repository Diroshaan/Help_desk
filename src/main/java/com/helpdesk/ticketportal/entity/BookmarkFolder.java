package com.helpdesk.ticketportal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * F3 - Ticket Lifecycle & Interaction Portal (Amarasinghe S. D, IT25103424)
 *
 * A student-created folder used to organise bookmarked tickets (see
 * TicketPortalService's "folder organization" logic).
 *
 * studentId is kept as a plain Long reference (not @ManyToOne), matching the
 * same pattern used by Feedback, so this stays decoupled from the profile
 * package.
 */

@Entity
@Table(name = "bookmark_folders")
public class BookmarkFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Student ID is required")
    @Column(nullable = false)
    private Long studentId;

    @NotBlank(message = "Folder name is required")
    @Column(nullable = false)
    private String name;

    private LocalDateTime createdAt = LocalDateTime.now();

    //Constructors
    public BookmarkFolder() {}    // Required no-argument constructor for JPA

    //Getters and setters
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
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
