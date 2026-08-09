package com.helpdesk.common;

/**
 * The three system roles described in the proposal's RBAC requirements (5.1).
 * Lives in 'common' because every package's security rules reference it -
 * it doesn't belong to any single person's feature.
 */
public enum Role {
    STUDENT,
    OFFICER,
    ADMIN
}
