package com.helpdesk.ticketportal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Locale;    //for region sensitive operations

@Entity
@Table(
    name = "bookmark_folders",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "name_key"})
)
public class BookmarkFolder {

    //IDs are auto-incremented by database
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //ensures studentID is present
    @NotNull(message = "Student ID is required")
    @Column(nullable = false)
    private Long studentId;

    //ensures folder name is not blank text
    @NotBlank(message = "Folder name is required")
    @Column(nullable = false)
    private String name;

    //defaults to system time when the object is created
    private LocalDateTime createdAt = LocalDateTime.now();

    //stores folder color and ensures color is valid
    @Size(max = 7, message = "Colour must be a hex value such as #2563eb")
    @Column(length = 7)
    private String colour;

    //prevents duplicate folder names based off case
    @Column(name = "name_key", nullable = false, length = 60)
    private String nameKey;

    //convert nameKey into lowercase before bookmark folder object is saved to database
    @PrePersist
    @PreUpdate
    private void syncNameKey() {
        //lowercase conversion performed using neutral language rather than server default language
        this.nameKey = (name == null) ? null : name.toLowerCase(Locale.ROOT);
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
