package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helpdesk.common.validation.ValidationRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/officers/me (US-04).
 *
 * The same partial-update contract as the student's ProfileUpdateRequest, and
 * for the same reason: the page saves details and preferences with separate
 * buttons, so each request carries only some fields. A null field means "the
 * caller is not changing this", never "clear it".
 *
 * That is why the booleans are Boolean, not boolean. A primitive boolean cannot
 * be null - an absent field would arrive as false and silently switch the
 * officer's notifications off every time they saved their name. That exact bug
 * happened on the student profile once; this class is written so it cannot.
 *
 * What is NOT here matters as much as what is: no email, staff number, job
 * title or departments. See OfficerProfileResponse for why those are read-only.
 * A request that sends them is not rejected - Jackson ignores unknown
 * properties - it simply changes nothing, because there is nowhere for the
 * value to go.
 */
public class OfficerProfileUpdateRequest {

    // "Not blank if present" - @Pattern treats null as valid, so an absent
    // name passes and is skipped, while "" or "   " is rejected. @NotBlank
    // could not tell those two apart. (The student DTO has the full story.)
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
