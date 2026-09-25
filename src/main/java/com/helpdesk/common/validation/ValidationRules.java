package com.helpdesk.common.validation;

/**
 * Input rules that more than one request type must apply identically.
 *
 * WHY A CONSTANTS CLASS
 * ---------------------
 * The password rule used to be written out in full on RegistrationRequest.
 * Password change now needs exactly the same rule, and a rule copied into two
 * places drifts: someone tightens it on one form and a weaker password gets in
 * through the other. Annotation attributes must be compile-time constants, so a
 * validator bean cannot be injected here - a class of `static final String`s is
 * the one way to share a regex across @Pattern annotations, and it makes "what
 * is our password policy" answerable by opening one file.
 *
 * Only rules for fields I own live here (F1 and shared auth). Other features'
 * rules stay in their own packages.
 */
public final class ValidationRules {

    private ValidationRules() {
        // Constants only - never instantiated.
    }

    /**
     * At least 8 characters, with a lower-case letter, an upper-case letter and
     * a digit somewhere in it.
     */
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";

    public static final String PASSWORD_MESSAGE =
            "Password must be at least 8 characters and include an upper-case letter, a lower-case letter, and a digit";

    /**
     * 72 is not an arbitrary number. BCrypt, which hashes every password in this
     * system, only reads the first 72 bytes of its input and silently ignores the
     * rest. Without an upper limit, two different 80-character passwords sharing
     * their first 72 characters would both unlock the same account, and the
     * user would never know the tail of their password did nothing. Refusing
     * the long password up front is honest; truncating it quietly is not.
     */
    public static final int PASSWORD_MAX_LENGTH = 72;

    public static final String PASSWORD_LENGTH_MESSAGE = "Password must be 72 characters or fewer";

    /**
     * A phone number: optional leading +, then a digit, then 6-19 more digits,
     * spaces, hyphens or brackets. "077 411 5171", "+94 77 411 5171" and
     * "(011) 2345678" all pass; letters, and anything too short to be a real
     * number, do not.
     *
     * Deliberately loose on FORMAT and strict on CHARACTERS. Phone formats vary
     * by country and by habit, so insisting on one layout would reject real
     * numbers; but no phone number contains a letter or a semicolon, so those
     * are refused. The blank alternative lets an optional box be submitted
     * empty, which the service then treats as "no number".
     *
     * Surrounding whitespace (\\s*) is tolerated because validation runs BEFORE
     * the value is trimmed - a number pasted as " 077 411 5171 " is a perfectly
     * good number, and rejecting it for an invisible space would be the kind of
     * error a user cannot see to fix. Student.setContactNumbers trims it.
     * (Found by testing: the first version of this pattern rejected exactly
     * that paste.)
     */
    public static final String PHONE_REGEX = "^\\s*$|^\\s*\\+?[0-9][0-9 ()\\-]{6,19}\\s*$";

    public static final String PHONE_MESSAGE =
            "Phone number may contain only digits, spaces, hyphens, brackets and a leading +";

    /** How many contact numbers a student may keep. See Student.contactNumbers. */
    public static final int MAX_CONTACT_NUMBERS = 3;
}
