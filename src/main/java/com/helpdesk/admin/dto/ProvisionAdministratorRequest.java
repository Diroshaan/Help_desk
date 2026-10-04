package com.helpdesk.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/admin/administrators. Like ProvisionOfficerRequest it has no id,
 * active or provisionedBy fields. staffNumber is optional here because the first admin
 * is a bootstrap account created before any staff numbers exist.
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
