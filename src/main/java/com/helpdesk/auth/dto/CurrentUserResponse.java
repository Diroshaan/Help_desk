package com.helpdesk.auth.dto;

import com.helpdesk.common.user.entity.AppUser;

/**
 * SHARED / CROSS-CUTTING - "who is logged in", for every account type.
 *
 *
 * WHY THIS EXISTS
 * ---------------
 * Until now the only way for the frontend to ask "who am I" was
 * GET /api/students/me, which resolves the caller through StudentService and so
 * only ever searches the students table. The consequence was not a small one: an
 * officer or an administrator could authenticate successfully and then get 403
 * from that endpoint, and useSession.jsx reads a 403 there as "nobody is logged
 * in". So a real officer logged in and the interface immediately showed them as
 * a guest - which meant no officer or admin screen could be built or tested at
 * all, even though the endpoints behind them were finished.
 *
 * This is the account-type-neutral answer to the same question. It resolves
 * through AppUserRepository, which is polymorphic across the JOINED hierarchy,
 * so one endpoint serves students, officers and administrators without knowing
 * in advance which it is talking to.
 *
 *
 * WHY A RECORD, AND WHY NOT JUST RETURN AppUser
 * ---------------------------------------------
 * AppUser carries a password hash. It is annotated WRITE_ONLY so Jackson would
 * not serialise it today, but that is one annotation standing between a
 * credential and an HTTP response, on a class four other features also edit.
 * A DTO cannot leak a field it does not have, which is a structural guarantee
 * rather than a remembered one. This is the same rule the rest of the project
 * follows - no controller in this codebase returns an entity.
 *
 * A record rather than a class because this is genuinely just four values with
 * no behaviour: the compiler writes the constructor, the accessors, equals and
 * hashCode, and there is nothing left to get wrong.
 *
 *
 * WHY 'role' IS A STRING HERE
 * ---------------------------
 * The frontend routes on it (student -> /#/profile, officer -> /#/queue, admin
 * -> /#/admin) and compares it to a literal. Serialising the enum by name()
 * makes that contract explicit and stable: "STUDENT", "OFFICER", "ADMIN", the
 * same three strings SecurityConfig's hasRole() checks against once "ROLE_" is
 * prefixed. Returning the enum object itself would serialise the same way today
 * but would change silently if anyone ever added a field to Role.
 */
public record CurrentUserResponse(
        Long id,
        String email,
        String role,
        String displayName) {

    /**
     * Build the response from whichever kind of account is logged in.
     *
     * Note there is no instanceof and no cast in here, which is the whole point
     * of the two abstract methods on AppUser: getRole() answers "what kind of
     * account is this" and getDisplayName() answers "what is it called", and
     * each subtype has already been made to answer both. Adding a fourth
     * account type would not require editing this method - the compiler would
     * force the new subclass to supply the answers instead.
     */
    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName());
    }
}
