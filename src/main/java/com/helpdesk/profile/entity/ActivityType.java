package com.helpdesk.profile.entity;

/** Kinds of account event the activity log records. */
public enum ActivityType {

    ACCOUNT_CREATED,

    LOGGED_IN,

    /** Name, phone, faculty or profile picture changed. */
    PROFILE_UPDATED,

    /** A notification toggle changed. */
    PREFERENCES_UPDATED,

    ACCOUNT_DEACTIVATED,

    /** Shown so a student can spot a change they didn't make. Never includes the password. */
    PASSWORD_CHANGED
}
