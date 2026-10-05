package com.helpdesk.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Body for POST /api/admin/officers. There are no id, active or provisionedBy fields on
 * purpose: the server sets those, so a client can't create a disabled account or credit
 * someone else as the provisioner.
 * The password rule lives here, not on Officer, because the entity only ever holds the
 * BCrypt hash. WRITE_ONLY stops the password being written back out in JSON.
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

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be 120 characters or fewer")
        String fullName,

        // Department codes the officer serves. At least one, or the officer would see no
        // tickets in the queue; the service checks each code exists and is active.
        @NotEmpty(message = "Choose at least one department for this officer")
        @Size(max = 10, message = "An officer can serve at most 10 departments")
        Set<String> departmentCodes
) {
}
