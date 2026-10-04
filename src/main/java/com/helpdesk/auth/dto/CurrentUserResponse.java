package com.helpdesk.auth.dto;

import com.helpdesk.common.user.entity.AppUser;

/**
 * "Who is logged in" for any account type. A DTO so the password hash can never be sent.
 * role is "STUDENT", "OFFICER" or "ADMIN" - the frontend routes on it.
 */
public record CurrentUserResponse(
        Long id,
        String email,
        String role,
        String displayName) {

    // No instanceof needed: every subtype implements getRole() and getDisplayName().
    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName());
    }
}
