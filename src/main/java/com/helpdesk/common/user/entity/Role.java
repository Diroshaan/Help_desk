package com.helpdesk.common.user.entity;

/**
 * The three account types. Spring Security authorities are built as "ROLE_" + name(), and
 * SecurityConfig uses hasRole("OFFICER") / hasRole("ADMIN"), so don't rename these.
 */
public enum Role {

    STUDENT,

    OFFICER,

    ADMIN
}
