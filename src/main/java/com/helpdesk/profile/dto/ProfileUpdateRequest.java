package com.helpdesk.profile.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /api/students/{id} - profile self-editing (US-01).
 *
 * Why this exists instead of just reusing the Student entity as the request
 * body (which the endpoint did originally): Student carries EVERY field the
 * entity has, including ones that must never be blindly overwritten by a
 * profile edit:
 *   - password: @NotBlank on Student, but GET responses never return it
 *     (WRITE_ONLY - see Student.java), so the frontend has nothing to put
 *     there. Reusing Student for updates forced the caller to re-submit a
 *     password on every edit just to satisfy validation, even though
 *     StudentService never actually used that value to change anything.
 *   - studentId / email / role: not meant to change via this endpoint at
 *     all (email doubles as the login identity used by isOwnProfile();
 *     role is deliberately locked down against self-escalation, same
 *     reasoning as in StudentService.register()). Accepting them here would
 *     be silently ignored (StudentService.updateProfile() doesn't read them
 *     off the entity), which is confusing - the request shape *implies*
 *     those fields can be changed when they can't.
 *
 * A dedicated request DTO fixes both problems: it only has the fields this
 * endpoint is actually allowed to change, so there's no confusing "field is
 * accepted but silently ignored" behaviour, and no unrelated password
 * requirement blocking an otherwise valid profile edit.
 */
public class ProfileUpdateRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Department is required")
    private String department;

    private String contactNumber;

    private String profilePictureUrl;

    private boolean emailNotificationsEnabled;

    private boolean portalNotificationsEnabled;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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
}
