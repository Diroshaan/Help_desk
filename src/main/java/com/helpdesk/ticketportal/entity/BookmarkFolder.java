package com.helpdesk.ticketportal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "bookmark_folders",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "name_key"})
)
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

    @Size(max = 7, message = "Colour must be a hex value such as #2563eb")
    @Column(length = 7)
    private String colour;

    @Column(name = "name_key", nullable = false, length = 60)
    private String nameKey;

    @PrePersist
    @PreUpdate
    private void syncNameKey() {
        this.nameKey = (name == null) ? null : name.toLowerCase();
    }

    //Constructors
    public BookmarkFolder() {}    // Required no-argument constructor for JPA

    //Getter and setter methods
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
    public String getColour() {
        return colour;
    }
    public void setColour(String colour) {
        this.colour = colour;
    }
}
