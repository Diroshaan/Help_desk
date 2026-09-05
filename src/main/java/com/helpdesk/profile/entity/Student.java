package com.helpdesk.profile.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    // @NotBlank alone accepts literally any non-empty string, so "x" or "not an id"
    // would register a real account. @Pattern pins it to the university's actual
    // format - two letters then eight digits, e.g. IT25101580.
    //
    // Safe to put directly on the entity (unlike the password rule below): unlike
    // password, studentId is never transformed after input - ProfileUpdateRequest
    // doesn't even expose it for editing - so whatever value passes this check at
    // registration keeps satisfying it on every later save. There's no equivalent
    // of "the hash won't match the plaintext pattern" risk here.
    @NotBlank(message = "Student ID is required")
    @Pattern(regexp = "^[A-Z]{2}\\d{8}$", message = "Student ID must be two letters followed by eight digits, e.g. IT25101580")
    @Column(unique = true, nullable = false)
    private String studentId;

    @NotBlank(message = "Full name is required")
    private String fullName;

    // @NotBlank is required IN ADDITION to @Email: Hibernate Validator's @Email
    // deliberately treats both null and "" as valid (it validates format only, and
    // leaves "is this field required?" to @NotBlank). Without it, {"email": ""}
    // registers an account with a blank login identity, and {"email": null} passes
    // validation only to hit the nullable=false column constraint below - surfacing
    // as a 500 instead of a clean 400, since GlobalExceptionHandler has no handler
    // for DataIntegrityViolationException.
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Column(unique = true, nullable = false)
    private String email;

    // WRITE_ONLY: accepted on registration (JSON in), but never included in a JSON
    // response back out. This is important - never let a password (even hashed)
    // leak into an API response. Compare this with @JsonIgnore, which would block
    // it in BOTH directions and break registration entirely.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Password is required")
    private String password;

    // Kept as a plain String (not a Java enum) for now, deliberately - Officer and
    // Admin accounts don't exist as entities yet (that's F6, Sprint 3). Defaulting
    // everyone who registers through this endpoint to "STUDENT" keeps this scoped
    // correctly to what Sprint 1 actually needs.
    private String role = "STUDENT";

    // No @NotBlank here, deliberately: the faculty dropdown's first option is
    // "Not set" with value "" (see DEPARTMENTS in Register.jsx / Profile.jsx),
    // so a student clearing their faculty via a profile update is a legitimate
    // save, not an invalid one - see the identical note on
    // ProfileUpdateRequest.department. This constraint has to live off the
    // entity for that to actually work: Hibernate re-validates every field of
    // Student on every save (registration AND later profile edits alike), so
    // even with the DTO-level check removed, a @NotBlank here would still
    // reject a save that sets this field to "" - which is exactly what
    // clearing the faculty needs to do.
    private String department;

    // The frontend calls this field "phone" in every JSON payload it sends and
    // reads (Register.jsx, Profile.jsx) - never "contactNumber". @JsonProperty
    // fixes both directions at once: without it, an incoming "phone" key
    // silently fails to bind (Jackson ignores unrecognised properties instead
    // of erroring), and an outgoing response serialises this field as
    // "contactNumber", which the frontend's `student.phone` read never finds -
    // so the phone box on the profile page rendered empty even when a number
    // was genuinely stored. The Java-side name stays contactNumber (matching
    // the rest of this codebase); only the wire format changes.
    @JsonProperty("phone")
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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
