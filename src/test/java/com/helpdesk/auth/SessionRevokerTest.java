package com.helpdesk.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SessionRevoker against the REAL SessionRegistryImpl, not a mock.
 *
 * A mocked registry would only prove that SessionRevoker calls the methods I
 * expected it to call. The bug this class exists to prevent is subtler - a
 * suspended user whose session quietly survives - so the test checks the
 * outcome that matters: after revocation, is that session marked expired, and
 * is everybody else's left alone?
 */
class SessionRevokerTest {

    private SessionRegistryImpl registry;
    private SessionRevoker revoker;

    private final UserDetails alice = User.withUsername("alice@my.sliit.lk").password("x").roles("STUDENT").build();
    private final UserDetails bob = User.withUsername("bob@my.sliit.lk").password("x").roles("STUDENT").build();

    @BeforeEach
    void setUp() {
        registry = new SessionRegistryImpl();
        revoker = new SessionRevoker(registry);
        registry.registerNewSession("alice-laptop", alice);
        registry.registerNewSession("alice-phone", alice);
        registry.registerNewSession("bob-laptop", bob);
    }

    @Test
    @DisplayName("Suspending an account expires every one of its sessions and no one else's")
    void revokeAllEndsOnlyThatUsersSessions() {
        revoker.revokeAllSessionsFor("alice@my.sliit.lk");

        assertThat(expired("alice-laptop")).isTrue();
        assertThat(expired("alice-phone")).isTrue();
        assertThat(expired("bob-laptop")).isFalse();
    }

    // Emails are case-insensitive in practice; a revocation that missed
    // "Alice@..." would leave a suspended user signed in.
    @Test
    @DisplayName("Matching the account ignores letter case in the email")
    void matchingIsCaseInsensitive() {
        revoker.revokeAllSessionsFor("ALICE@my.sliit.lk");

        assertThat(expired("alice-laptop")).isTrue();
    }

    @Test
    @DisplayName("After a password change, the session that made it survives and the others end")
    void revokeOthersKeepsTheCurrentSession() {
        revoker.revokeOtherSessions("alice@my.sliit.lk", "alice-laptop");

        assertThat(expired("alice-laptop")).isFalse();
        assertThat(expired("alice-phone")).isTrue();
        assertThat(expired("bob-laptop")).isFalse();
    }

    @Test
    @DisplayName("A blank email revokes nothing rather than everything")
    void blankEmailIsANoOp() {
        revoker.revokeAllSessionsFor("  ");

        assertThat(expired("alice-laptop")).isFalse();
        assertThat(expired("bob-laptop")).isFalse();
    }

    private boolean expired(String sessionId) {
        SessionInformation info = registry.getSessionInformation(sessionId);
        return info.isExpired();
    }
}
