package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

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

    // @Pattern instead of @NotBlank: this endpoint accepts PARTIAL updates (see
    // StudentService.updateProfile) - "Save preferences" on the frontend submits
    // only the three notification booleans, with fullName absent entirely. A
    // required-field annotation like @NotBlank treats that absence as invalid
    // and rejects the request with 400 before it even reaches the service, which
    // would make that save path permanently broken. @Pattern doesn't have that
    // problem: Bean Validation defines null as valid for every constraint except
    // @NotNull/@NotBlank/@NotEmpty, so an omitted field passes validation here
    // and is then left untouched by the null-check in updateProfile - while an
    // explicitly blank or whitespace-only string ("", "   ") still fails the
    // "at least one non-whitespace character" pattern and gets a 400. Absent and
    // blank need to mean different things on a partial-update DTO; @NotBlank
    // can't tell them apart, @Pattern can.
    @Pattern(regexp = ".*\\S.*", message = "Full name cannot be blank")
    private String fullName;

    // No blank-rejecting constraint at all, deliberately: the faculty dropdown's
    // first option is "Not set" with value "" (see DEPARTMENTS in Register.jsx /
    // Profile.jsx), so a student clearing their faculty is a legitimate save, not
    // an invalid one. Only null (field genuinely absent, e.g. a preferences-only
    // save) should be left alone - see the null-check in updateProfile.
    private String department;

    // The profile form (frontend/src/pages/Profile.jsx) sends this field as
    // "phone" in its JSON payload, not "contactNumber" - see the identical note
    // on RegistrationRequest.contactNumber for why @JsonProperty is required
    // here: without it, "phone" would silently fail to bind (Jackson ignores
    // unrecognised properties rather than erroring) and, before the null-check
    // fix in updateProfile existed, would have wiped the stored number outright.
    @JsonProperty("phone")
    private String contactNumber;

    private String profilePictureUrl;

    // Boolean (wrapper), not boolean (primitive) - deliberately. "Save changes"
    // (personal details) submits fullName/phone/department but NOT these two
    // preference fields, and "Save preferences" submits neither this class's
    // notification fields nor most of its other fields at all (the frontend's
    // three preference toggles - notifyOnReply/notifyOnStatus/weeklyDigest -
    // don't even share these fields' names; that's a separate, pre-existing gap,
    // not something this fix addresses). Either way, a primitive boolean field
    // has no way to represent "the client didn't send this" - Jackson leaves it
    // at its default, which for a primitive is always false. That false then
    // looked exactly like "please switch this preference off" to
    // updateProfile(), so saving your name silently turned both notification
    // toggles off on every edit. A Boolean wrapper can be null, so "absent" and
    // "explicitly set to false" are distinguishable again, and the null-check in
    // updateProfile treats only the first one as "leave it alone".
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

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
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
