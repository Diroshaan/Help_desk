package com.helpdesk.auth;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ends a user's live sessions, e.g. when an account is suspended or deactivated. The
 * .disabled() check only runs at login, so without this a suspended user who is already
 * signed in could carry on. Relies on AuthController registering each session in the
 * SessionRegistry after login (we log in manually, so Spring doesn't do it for us).
 */
@Component
public class SessionRevoker {

    private final SessionRegistry sessionRegistry;

    public SessionRevoker(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    // Expired sessions get a 401 on their next request. No sessions is normal, not an error.
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

    // After a password change: end every other session but keep the one that made the change.
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

    // Compares by username; falls back to toString() in case the principal is ever a plain String.
    private boolean matches(Object principal, String email) {
        if (principal instanceof UserDetails details) {
            return email.equalsIgnoreCase(details.getUsername());
        }
        return email.equalsIgnoreCase(String.valueOf(principal));
    }
}
