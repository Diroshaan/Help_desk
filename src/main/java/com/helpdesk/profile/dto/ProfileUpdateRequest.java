package com.helpdesk.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helpdesk.common.validation.ValidationRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

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
    // Length limits on this DTO mirror the Student entity's column lengths, for
    // the reason written out in full on RegistrationRequest: a limit checked
    // here produces a clean field message, while the same limit left to the
    // entity fails later inside Hibernate with its internal class names in the
    // text. @Size, like @Pattern, treats null as valid - so an omitted field on
    // a partial update still passes, exactly as the note above requires.
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    private String fullName;

    // The two name parts, for a client that edits them separately. Same partial
    // update rules as every other field here: absent means "leave it alone".
    //
    // givenName uses the same "not blank if present" @Pattern as fullName,
    // because the given name is the one part every student must keep. surname
    // has no such rule - sending "" is how a student with no family name
    // clears it, and the entity stores that as NULL.
    //
    // If fullName AND the parts are both sent, the parts win (see
    // StudentService.updateProfile): they are the more precise statement.
    @Pattern(regexp = ".*\\S.*", message = "Given name cannot be blank")
    @Size(max = 120, message = "Given name must be 120 characters or fewer")
    private String givenName;

    @Size(max = 120, message = "Surname must be 120 characters or fewer")
    private String surname;

    // No blank-rejecting constraint at all, deliberately: the faculty dropdown's
    // first option is "Not set" with value "" (see DEPARTMENTS in Register.jsx /
    // Profile.jsx), so a student clearing their faculty is a legitimate save, not
    // an invalid one. Only null (field genuinely absent, e.g. a preferences-only
    // save) should be left alone - see the null-check in updateProfile.
    // @Size accepts "" as well as null, so clearing the faculty still works.
    @Size(max = 100, message = "Faculty must be 100 characters or fewer")
    private String department;

    // The profile form (frontend/src/pages/Profile.jsx) sends this field as
    // "phone" in its JSON payload, not "contactNumber" - see the identical note
    // on RegistrationRequest.contactNumber for why @JsonProperty is required
    // here: without it, "phone" would silently fail to bind (Jackson ignores
    // unrecognised properties rather than erroring) and, before the null-check
    // fix in updateProfile existed, would have wiped the stored number outright.
    //
    // Now means "the FIRST contact number" - editing it keeps any others the
    // student has saved (see Student.setPrimaryContactNumber). Sending "" clears
    // just that one.
    @JsonProperty("phone")
    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
    private String contactNumber;

    // The whole list, replacing what is stored. Absent (null) leaves the numbers
    // alone; an empty list [] removes them all - the one place in this DTO where
    // "empty" and "absent" are deliberately different instructions, which is
    // why this is a List and not a comma-separated string.
    // The cap here is on what was SENT and is only a sanity bound against an
    // absurd payload; the real limit of three is applied to the cleaned list
    // (blanks and duplicates removed) in Student.setContactNumbers.
    @Size(max = 10, message = "Too many contact numbers were sent")
    private List<@Size(max = 30, message = "Phone number must be 30 characters or fewer")
                 @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
                 String> phones;

    // profilePictureUrl is no longer editable here. The server sets it when a
    // picture is uploaded, and only ever to its own download endpoint - see
    // the note on RegistrationRequest for the tracking-pixel problem a
    // client-writable URL caused. A request that still sends it is ignored
    // (Jackson skips unknown properties), which is safe: nothing changes.

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
