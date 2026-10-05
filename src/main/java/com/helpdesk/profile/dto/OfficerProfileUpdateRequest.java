package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helpdesk.common.validation.ValidationRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update for PUT /api/officers/me: a null field means "leave it as it is".
 * The toggles are Boolean, not boolean, so a missing field isn't read as false.
 * There are no fields for email, staff number, job title or departments on purpose.
 */
public class OfficerProfileUpdateRequest {

    // null passes (field skipped) but "" or spaces are rejected
    @Pattern(regexp = ".*\\S.*", message = "Full name cannot be blank")
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    private String fullName;

    @JsonProperty("phone")
    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
    private String contactNumber;

    private Boolean emailNotificationsEnabled;

    private Boolean portalNotificationsEnabled;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public Boolean getEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public Boolean getPortalNotificationsEnabled() {
        return portalNotificationsEnabled;
    }

    public void setPortalNotificationsEnabled(Boolean portalNotificationsEnabled) {
        this.portalNotificationsEnabled = portalNotificationsEnabled;
    }
}
