package com.helpdesk.notification;

import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.notification.channel.NotificationChannel;
import com.helpdesk.notification.service.NotificationMessage;
import com.helpdesk.notification.service.NotificationRecipient;
import com.helpdesk.notification.service.NotificationService;
import com.helpdesk.profile.entity.Student;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Strategy part: does NotificationService pick the right
 * channels from a user's preferences?
 *
 * The channels here are small fakes written in the test, not the real
 * portal/email classes. That's deliberate. If the context works with
 * channels it has never seen, it clearly isn't depending on any particular
 * implementation, which is the whole claim the Strategy pattern makes.
 */
class NotificationServiceTest {

    /** A channel that records what it was asked to send. */
    static class RecordingChannel implements NotificationChannel {
        final String name;
        final Predicate<NotificationRecipient> enabled;
        final List<String> sent = new ArrayList<>();

        RecordingChannel(String name, Predicate<NotificationRecipient> enabled) {
            this.name = name;
            this.enabled = enabled;
        }

        @Override public String getName() { return name; }
        @Override public boolean isEnabledFor(NotificationRecipient r) { return enabled.test(r); }
        @Override public void send(NotificationRecipient r, NotificationMessage m) { sent.add(m.title()); }
    }

    private final RecordingChannel portal = new RecordingChannel("portal", NotificationRecipient::portalEnabled);
    private final RecordingChannel email = new RecordingChannel("email", NotificationRecipient::emailEnabled);
    private final NotificationService service = new NotificationService(List.of(portal, email));
    private final NotificationMessage hello = new NotificationMessage("Hello", "Body", null);

    private static NotificationRecipient recipient(boolean email, boolean portal) {
        return new NotificationRecipient(1L, "nimal@my.sliit.lk", "Nimal", email, portal);
    }

    @Test
    @DisplayName("Both channels on: the message goes out on both")
    void bothChannels() {
        assertThat(service.notify(recipient(true, true), hello)).containsExactly("portal", "email");
        assertThat(portal.sent).containsExactly("Hello");
        assertThat(email.sent).containsExactly("Hello");
    }

    @Test
    @DisplayName("Email switched off: only the portal channel is used")
    void emailOff() {
        assertThat(service.notify(recipient(false, true), hello)).containsExactly("portal");
        assertThat(email.sent).isEmpty();
    }

    @Test
    @DisplayName("Everything switched off: nothing is sent")
    void allOff() {
        assertThat(service.notify(recipient(false, false), hello)).isEmpty();
        assertThat(portal.sent).isEmpty();
        assertThat(email.sent).isEmpty();
    }

    @Test
    @DisplayName("A failing channel doesn't stop the others")
    void failingChannelIsIsolated() {
        NotificationChannel broken = new RecordingChannel("broken", r -> true) {
            @Override public void send(NotificationRecipient r, NotificationMessage m) {
                throw new IllegalStateException("mail server down");
            }
        };
        NotificationService withBroken = new NotificationService(List.of(broken, portal));

        assertThat(withBroken.notify(recipient(true, true), hello)).containsExactly("portal");
        assertThat(portal.sent).containsExactly("Hello");
    }

    @Test
    @DisplayName("A new channel works without changing NotificationService")
    void newChannelNeedsNoChanges() {
        RecordingChannel sms = new RecordingChannel("sms", r -> true);
        NotificationService withSms = new NotificationService(List.of(portal, email, sms));

        assertThat(withSms.notify(recipient(false, false), hello)).containsExactly("sms");
    }

    @Test
    @DisplayName("A student's recipient carries their own toggles")
    void studentPreferencesAreRead() {
        Student s = new Student();
        s.setId(7L);
        s.setEmail("s@my.sliit.lk");
        s.setFullName("Nimal Perera");
        s.setEmailNotificationsEnabled(false);
        s.setPortalNotificationsEnabled(true);

        NotificationRecipient r = NotificationRecipient.from(s);

        assertThat(r.emailEnabled()).isFalse();
        assertThat(r.portalEnabled()).isTrue();
        assertThat(r.displayName()).isEqualTo("Nimal Perera");
    }

    @Test
    @DisplayName("Administrators have no preference screen, so they get every channel")
    void administratorGetsBoth() {
        Administrator admin = new Administrator();
        admin.setEmail("admin@helpdesk.local");

        NotificationRecipient r = NotificationRecipient.from(admin);

        assertThat(r.emailEnabled()).isTrue();
        assertThat(r.portalEnabled()).isTrue();
    }
}
