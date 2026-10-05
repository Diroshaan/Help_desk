package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helpdesk.common.validation.ValidationRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for PUT /api/students/{id}. Partial update: a null field is left
 * unchanged. Only has the fields a student may edit (no password, email or role).
 */
public class ProfileUpdateRequest {

    // @Pattern rather than @NotBlank: null (not sent) passes, but "" or spaces get a 400.
    // Lengths match the entity columns so the user gets a clean message.
    @Pattern(regexp = ".*\\S.*", message = "Full name cannot be blank")
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    private String fullName;

    // If both fullName and the name parts are sent, the parts win.
    // surname can be cleared with "" (students with no family name).
    @Pattern(regexp = ".*\\S.*", message = "Given name cannot be blank")
    @Size(max = 120, message = "Given name must be 120 characters or fewer")
    private String givenName;

    @Size(max = 120, message = "Surname must be 120 characters or fewer")
    private String surname;

    // "" is allowed: the faculty dropdown's "Not set" option clears it
    @Size(max = 100, message = "Faculty must be 100 characters or fewer")
    private String department;

    // The form sends "phone". This is the first contact number only; others are kept.
    @JsonProperty("phone")
    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
    private String contactNumber;

    // Replaces the whole list: null leaves it alone, [] removes all numbers.
    // 10 is just a sanity cap; the real limit of three is in Student.setContactNumbers.
    @Size(max = 10, message = "Too many contact numbers were sent")
    private List<@Size(max = 30, message = "Phone number must be 30 characters or fewer")
                 @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
                 String> phones;

    // No profilePictureUrl: the server sets it on upload, never the client.

    // Boolean, not boolean, so a missing field is null instead of false
    // (otherwise saving your name would switch notifications off).
    private Boolean emailNotificationsEnabled;

    private Boolean portalNotificationsEnabled;

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

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public List<String> getPhones() {
        return phones;
    }

    public void setPhones(List<String> phones) {
        this.phones = phones;
    }

    public Boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public Boolean isPortalNotificationsEnabled() {
        return portalNotificationsEnabled;
    }

    public void setPortalNotificationsEnabled(Boolean portalNotificationsEnabled) {
        this.portalNotificationsEnabled = portalNotificationsEnabled;
    }
}
