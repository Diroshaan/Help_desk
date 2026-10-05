package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helpdesk.profile.entity.Student;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What StudentController returns instead of the Student entity. Only the fields
 * listed here leave the app, and there is no password field, so the hash can't leak.
 */
public class StudentResponse {

    private final Long id;
    private final String studentId;

    // built from givenName + surname
    private final String fullName;
    private final String givenName;
    private final String surname;
    private final String email;

    // sent as "phone" because that's the name the frontend reads; it's the first number
    @JsonProperty("phone")
    private final String contactNumber;

    // never null, empty list when there are none
    private final List<String> phones;

    private final String department;
    private final String profilePictureUrl;

    // read-only: role is never accepted on the request side
    private final String role;

    private final boolean emailNotificationsEnabled;
    private final boolean portalNotificationsEnabled;

    // soft-delete flag, shown as Active/Suspended
    private final boolean active;

    private final LocalDateTime createdAt;

    /** Latest 20 entries on the student's own profile; empty (never null) everywhere else. */
    private final List<ActivityLogResponse> activityLog;

    public StudentResponse(Long id, String studentId, String fullName, String givenName, String surname,
                           String email, String contactNumber, List<String> phones,
                           String department, String profilePictureUrl,
                           String role, boolean emailNotificationsEnabled,
                           boolean portalNotificationsEnabled, boolean active,
                           LocalDateTime createdAt, List<ActivityLogResponse> activityLog) {
        this.id = id;
        this.studentId = studentId;
        this.fullName = fullName;
        this.givenName = givenName;
        this.surname = surname;
        this.email = email;
        this.contactNumber = contactNumber;
        // copy, so we don't hold on to Hibernate's lazy collection
        this.phones = phones == null ? List.of() : List.copyOf(phones);
        this.department = department;
        this.profilePictureUrl = profilePictureUrl;
        this.role = role;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.portalNotificationsEnabled = portalNotificationsEnabled;
        this.active = active;
        this.createdAt = createdAt;
        this.activityLog = activityLog == null ? List.of() : activityLog;
    }

    /** Without the activity log. */
    public static StudentResponse from(Student student) {
        return withActivity(student, List.of());
    }

    /** Named differently from from() so StudentResponse::from stays unambiguous as a method reference. */
    public static StudentResponse withActivity(Student student, List<ActivityLogResponse> activityLog) {
        return new StudentResponse(
                student.getId(),
                student.getStudentId(),
                student.getFullName(),
                student.getGivenName(),
                student.getSurname(),
                student.getEmail(),
                student.getPrimaryContactNumber(),
                student.getContactNumbers(),
                student.getDepartment(),
                student.getProfilePictureUrl(),
                student.getRole().name(),
                student.isEmailNotificationsEnabled(),
                student.isPortalNotificationsEnabled(),
                student.isActive(),
                student.getCreatedAt(),
                activityLog
        );
    }

    /** For the staff listing; no activity logs, to avoid one extra query per student. */
    public static List<StudentResponse> fromAll(List<Student> students) {
        return students.stream().map(StudentResponse::from).toList();
    }

    public Long getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getSurname() {
        return surname;
    }

    public List<String> getPhones() {
        return phones;
    }

    public String getEmail() {
        return email;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getDepartment() {
        return department;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public String getRole() {
        return role;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public boolean isPortalNotificationsEnabled() {
        return portalNotificationsEnabled;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<ActivityLogResponse> getActivityLog() {
        return activityLog;
    }
}
