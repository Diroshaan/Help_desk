package com.helpdesk.profile.entity;

/**
 * The kinds of account event the activity log records (F1 - "Dashboard &
 * Activity View").
 *
 * Why an enum rather than a free-text String column: the set of things that can
 * appear in this log is fixed and decided in code, so a typo like "PROFILE_UPDATE"
 * should be a compile error, not a row that quietly never matches a filter. It
 * also gives the UI something stable to switch on if it ever wants an icon per
 * event - the human-readable sentence stored alongside it (ActivityLog.description)
 * is for reading, this is for reasoning about.
 *
 * Note what is NOT here: failed login attempts, password resets and admin
 * actions. Those belong to stories that do not exist yet (US-05, and the
 * change-password endpoint). Adding a constant for an event nothing records
 * would be a promise the system does not keep.
 */
public enum ActivityType {

    /** The account row was created by POST /api/students (US-03). */
    ACCOUNT_CREATED,

    /** A successful authentication through POST /api/auth/login. */
    LOGGED_IN,

    /** Name, phone, faculty or profile picture changed (US-01). */
    PROFILE_UPDATED,

    /** One of the notification channel toggles changed (US-06). */
    PREFERENCES_UPDATED,

    /** Self-service deactivation through DELETE /api/students/{id} (US-02). */
    ACCOUNT_DEACTIVATED
}
