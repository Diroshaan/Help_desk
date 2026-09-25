package com.helpdesk.auth;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SHARED / CROSS-CUTTING - ends a user's active sessions on the server.
 *
 *
 * WHY THIS EXISTS
 * ---------------
 * The Scrum document's F1 sub-function "Account Deletion" reads:
 *
 *   "Process self-service account deletion/deactivation requests, SOFT-DELETING
 *    student records AND REVOKING SESSION TOKENS."
 *
 * The first half was built; the second half was not. Deactivating an account set
 * active = false and nothing else, so a student who was ALREADY logged in kept
 * working normally until their session happened to expire.
 *
 * It is tempting to think .disabled(!isActive()) in StudentUserDetailsService
 * already covers this. It does not, and the reason is worth understanding: that
 * check runs during AUTHENTICATION, once, when someone submits a password. It is
 * never consulted again for the life of the session. So it stops a deactivated
 * user from logging IN; it does nothing about the one who already did.
 *
 * That gap matters most for the case F1 does not own. When a student closes
 * their own account the browser signs itself out, so the session ends in
 * practice. But when an ADMINISTRATOR suspends someone (US-05) the suspended
 * user is at their own computer, and nothing tells their browser anything. They
 * keep raising tickets. Suspension that a suspended person can ignore is not
 * suspension.
 *
 *
 * HOW IT WORKS, AND WHY IT NEEDS HELP FROM AuthController
 * -------------------------------------------------------
 * Spring Security's SessionRegistry maps a principal to every session that
 * principal currently holds. expireNow() marks a session expired; the
 * ConcurrentSessionFilter then invalidates it on that session's next request and
 * the configured strategy answers 401.
 *
 * The catch: the registry is normally populated by Spring's
 * SessionAuthenticationStrategy, which runs inside the form-login filter. This
 * project authenticates by hand in AuthController, so that filter never runs and
 * the registry would stay permanently empty - and this class would silently do
 * nothing. AuthController therefore registers each session explicitly after a
 * successful login. If that call is ever removed, revocation stops working with
 * no error to show for it.
 *
 *
 * WHY THIS IS NOT IN StudentService
 * ---------------------------------
 * Ending a session is an authentication concern, and F6 needs exactly the same
 * behaviour when an administrator suspends an officer or another administrator.
 * Putting it in F1's service would mean F6 importing F1 to suspend an account
 * that is not a student.
 */
@Component
public class SessionRevoker {

    private final SessionRegistry sessionRegistry;

    public SessionRevoker(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * End every session currently held by this login address.
     *
     * Matching is on the principal's username rather than on object identity,
     * because the UserDetails in the registry was built at login and is a
     * different instance from anything a service holds now.
     *
     * includeExpiredSessions = false: a session already marked expired does not
     * need marking again.
     *
     * Silently does nothing when the user has no active session, which is the
     * normal case - most accounts are deactivated while their owner is not
     * logged in. That is not a failure and must not be treated as one.
     */
    public void revokeAllSessionsFor(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!matches(principal, email)) {
                continue;
            }
            List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
            for (SessionInformation session : sessions) {
                session.expireNow();
            }
        }
    }

    /**
     * The registry stores whatever was passed as the principal at login. This
     * project stores a UserDetails, but the check falls back to toString() so a
     * future change to a plain String principal does not silently stop
     * revocation working.
     */
    /**
     * End every session of this account EXCEPT the one making the request.
     *
     * Used after a password change. The reason to change a password is often
     * "I think someone else has it" - and if they are already signed in, a new
     * password on its own does nothing to them: their session was authenticated
     * with the old one and stays valid until it expires. So every other session
     * is ended. The session that made the change is kept, because the person
     * who just proved they know the current password is the one entitled to
     * carry on; signing them out as a reward would be hostile for no benefit.
     *
     * keepSessionId may be null (no session to keep), in which case this is
     * the same as revokeAllSessionsFor.
     */
    public void revokeOtherSessions(String email, String keepSessionId) {
        if (email == null || email.isBlank()) {
            return;
        }
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!matches(principal, email)) {
                continue;
            }
            for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                if (!session.getSessionId().equals(keepSessionId)) {
                    session.expireNow();
                }
            }
        }
    }

    private boolean matches(Object principal, String email) {
        if (principal instanceof UserDetails details) {
            return email.equalsIgnoreCase(details.getUsername());
        }
        return email.equalsIgnoreCase(String.valueOf(principal));
    }
}
