package com.helpdesk.auth.dto;

import com.helpdesk.common.validation.ValidationRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/auth/password.
 *
 * WHY THE CURRENT PASSWORD IS REQUIRED EVEN THOUGH THE USER IS SIGNED IN
 * ----------------------------------------------------------------------
 * Being signed in proves that somebody had the password at some point - not
 * that the person at the keyboard now is that somebody. A laptop left open in
 * a library, or a session cookie stolen from a shared machine, would otherwise
 * let a stranger change the password and lock the real owner out of their own
 * account permanently. Asking for the current password turns "has this
 * browser" into "knows the secret", which is the thing a password is for.
 *
 * The new password gets exactly the rule registration uses - both read it from
 * ValidationRules, so the two can never disagree.
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
