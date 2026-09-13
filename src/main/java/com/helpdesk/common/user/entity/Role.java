package com.helpdesk.common.user.entity;

/**
 * SHARED USER MODEL - not owned by any single feature.
 *
 * The three kinds of account the system recognises, from the requirement
 * specification section 2: Students (end users), Help Desk Officers (support
 * handlers) and System Administrators (platform managers).
 *
 *
 * WHY THIS IS AN ENUM AND NOT THE String IT REPLACED
 * --------------------------------------------------
 * Student.role used to be a plain String defaulting to "STUDENT", and
 * StudentUserDetailsService built Spring Security authorities by writing
 * "ROLE_" + student.getRole(). Nothing validated what went into that column, so
 * a row holding null produced the authority "ROLE_null", and a row holding
 * "STUDENT " with a trailing space produced an authority that silently matched
 * no rule anywhere. Both fail open in the confusing direction - the user is
 * authenticated but has no usable authority, and nothing reports why.
 *
 * An enum makes those states unrepresentable rather than merely unlikely.
 *
 *
 * WHY THE NAMES MUST NOT CHANGE
 * -----------------------------
 * SecurityConfig writes hasRole("OFFICER") and hasRole("ADMIN"), and
 * StudentController.isOfficerOrAdmin() compares against the literal strings
 * "ROLE_OFFICER" and "ROLE_ADMIN". Spring Security's hasRole("X") checks for an
 * authority named "ROLE_X", and the authority is built from name() below.
 * Renaming a constant here silently switches off an access rule there - the
 * application still starts, the rule still exists, and it just never matches.
 * If a name ever has to change, all three places change together.
 */
public enum Role {

    /** A university student. Self-registers; can only ever reach their own data. */
    STUDENT,

    /** Help desk staff. Works departmental queues and publishes knowledge base articles. */
    OFFICER,

    /** Platform manager. Provisions privileged accounts and sees system-wide analytics. */
    ADMIN
}
