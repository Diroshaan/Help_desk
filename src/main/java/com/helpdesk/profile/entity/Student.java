package com.helpdesk.profile.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * F1 - Student Profile & Preferences Management (Diroshaan S, IT25101580)
 *
 * This is a SAMPLE entity to show the pattern every team member should follow
 * for their own package. Copy this structure for your own entities:
 *   - @Entity marks the class as a database table
 *   - @Id + @GeneratedValue for the primary key
 *   - Bean validation annotations (@NotBlank, @Email, etc.) enforce rules
 *     described in your proposal's Non-Functional Requirements (5.1 Input Validation)
 *   - Standard getters/setters below (no Lombok, so it's clear what's happening)
 */
@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Student ID is required")
    @Column(unique = true, nullable = false)
    private String studentId;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @Email(message = "Must be a valid email address")
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank(message = "Department is required")
    private String department;

    private String contactNumber;

    private String profilePictureUrl;

    // Notification channel preferences (F1: toggle Email / Portal Alerts)
    private boolean emailNotificationsEnabled = true;
    private boolean portalNotificationsEnabled = true;

    // Soft-deactivation flag, used by the self-service account deletion story (US-02)
    private boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    // --- Constructors ---

    public Student() {
        // Required no-argument constructor for JPA
    }

    // --- Getters and setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public boolean isPortalNotificationsEnabled() {
        return portalNotificationsEnabled;
    }

    public void setPortalNotificationsEnabled(boolean portalNotificationsEnabled) {
        this.portalNotificationsEnabled = portalNotificationsEnabled;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
