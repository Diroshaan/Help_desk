package com.helpdesk.auth.dto;

import com.helpdesk.common.validation.ValidationRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for PUT /api/auth/password. The current password is required so someone using an
 * unattended or stolen session can't lock the owner out. Same password rule as registration.
 */
public record PasswordChangeRequest(

        @NotBlank(message = "Your current password is required")
        String currentPassword,

        @NotBlank(message = "A new password is required")
        @Pattern(regexp = ValidationRules.PASSWORD_REGEX, message = ValidationRules.PASSWORD_MESSAGE)
        @Size(max = ValidationRules.PASSWORD_MAX_LENGTH, message = ValidationRules.PASSWORD_LENGTH_MESSAGE)
        String newPassword
) {
}
