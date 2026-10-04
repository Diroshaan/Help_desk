package com.helpdesk.common.validation;

/**
 * Validation rules shared by several request DTOs (e.g. registration and password change).
 * Constants, because annotation attributes like @Pattern must be compile-time values.
 */
public final class ValidationRules {

    private ValidationRules() {
        // constants only
    }

    // 8+ characters with a lower-case letter, an upper-case letter and a digit.
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";

    public static final String PASSWORD_MESSAGE =
            "Password must be at least 8 characters and include an upper-case letter, a lower-case letter, and a digit";

    // BCrypt only reads the first 72 bytes, so anything longer would be silently cut off.
    public static final int PASSWORD_MAX_LENGTH = 72;

    public static final String PASSWORD_LENGTH_MESSAGE = "Password must be 72 characters or fewer";

    /**
     * Optional +, then 7-20 digits, spaces, hyphens or brackets. Loose on layout, strict on
     * characters. Blank is allowed (optional field) and surrounding spaces are fine because
     * the value is trimmed after validation.
     */
    public static final String PHONE_REGEX = "^\\s*$|^\\s*\\+?[0-9][0-9 ()\\-]{6,19}\\s*$";

    public static final String PHONE_MESSAGE =
            "Phone number may contain only digits, spaces, hyphens, brackets and a leading +";

    public static final int MAX_CONTACT_NUMBERS = 3;
}
