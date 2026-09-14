package com.helpdesk.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Request body for POST /api/admin/administrators.
 *
 * Same anti-mass-assignment shape as ProvisionOfficerRequest - no id, no
 * active, no provisionedBy - for the same reasons.
 *
 * The one difference is staffNumber, which is optional here and required there.
 * That mirrors the entities exactly and the mismatch is deliberate: every help
 * desk officer is support staff with a number issued to them, whereas the first
 * administrator in a new deployment is a technical bootstrap account created
 * before anybody has been issued anything. See the comment on
 * Administrator.staffNumber - it stays unique when present, because a SQL
 * unique constraint permits multiple NULLs, so "optional but unique when set"
 * needs nothing given up to express.
 */
public record ProvisionAdministratorRequest(

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

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name must be 100 characters or fewer")
        String displayName,

        @Size(max = 20, message = "Staff number must be 20 characters or fewer")
        String staffNumber
) {
}
