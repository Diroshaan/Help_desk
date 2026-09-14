package com.helpdesk.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Request body for POST /api/admin/officers - provisioning a help desk officer.
 *
 * NO id, NO active, NO provisionedBy FIELD
 * ----------------------------------------
 * All three are decided server-side, and their absence is the control, not an
 * oversight. If 'active' existed here, a client could provision an account
 * already deactivated, or - far worse once the field is reused for edits - flip
 * any account's status through a create endpoint. If 'provisionedBy' existed, an
 * administrator could attribute their own provisioning to a colleague, which
 * destroys the audit trail requirement 3 exists to create. A field that is not
 * on the DTO cannot be bound from the request body no matter what Jackson is
 * asked to do.
 *
 * WHY THE PASSWORD RULE LIVES HERE AND NOT ON Officer
 * ---------------------------------------------------
 * Exactly the reasoning in profile/dto/RegistrationRequest.java: this is the
 * one and only moment the raw password exists. The service hashes it
 * immediately and the entity only ever holds a BCrypt hash - and a hash has no
 * reason to satisfy an "upper-case, lower-case, digit" rule written for
 * human-typed passwords. Putting the rule on the entity would make every later
 * save of that officer fail validation against a value nobody ever typed.
 *
 * WRITE_ONLY on the password so that if this object is ever echoed back - in a
 * debug endpoint, a log line, an error body - Jackson refuses to serialise it.
 *
 * No @Pattern on staffNumber, matching Officer: the requirement specification
 * publishes no staff number format, and inventing one here would reject valid
 * numbers the day a real one does not match the guess. @Size bounds it and the
 * format is left open until it is actually known.
 */
public record ProvisionOfficerRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 120, message = "Email must be 120 characters or fewer")
        String email,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "Password is required")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                 message = "Password must be at least 8 characters and include an upper-case letter, "
                         + "a lower-case letter and a digit")
        String password,

        @NotBlank(message = "Staff number is required")
        @Size(max = 20, message = "Staff number must be 20 characters or fewer")
        String staffNumber,

        @NotBlank(message = "Job title is required")
        @Size(max = 100, message = "Job title must be 100 characters or fewer")
        String jobTitle,

        // The officer's name.
        //
        // Added when Officer.fullName was added to the shared user model. Before
        // that the entity had nowhere to put a name, so this request did not
        // collect one and every provisioned officer was identified in the admin
        // listing by their job title - which is not unique and is not a name.
        //
        // Required, matching @NotBlank on the entity. Validating it in both
        // places is not duplication for its own sake: this catches it at the
        // edge of the system with a 400 and a field-level message the form can
        // show next to the input, where the entity constraint catches it at
        // flush time as a database error with no field attached to it.
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be 120 characters or fewer")
        String fullName
) {
}
