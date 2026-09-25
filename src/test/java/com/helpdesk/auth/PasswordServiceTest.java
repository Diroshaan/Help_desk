package com.helpdesk.auth;

import com.helpdesk.auth.dto.PasswordChangeRequest;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.notification.event.PasswordChangedEvent;
import com.helpdesk.profile.entity.ActivityType;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Password change, for any account type.
 *
 * A real BCrypt encoder (lowest strength, for speed) rather than a mock: the
 * rules here are about hashes - "does this match the stored hash", "is the new
 * value stored hashed" - and a mocked encoder would let a test pass while the
 * real comparison failed.
 */
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private SessionRevoker sessionRevoker;
    @Mock private ActivityLogService activityLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private PasswordService service;

    @BeforeEach
    void setUp() {
        service = new PasswordService(appUserRepository, encoder, sessionRevoker, activityLogService, eventPublisher);
    }

    private Student student(String rawPassword) {
        Student s = new Student();
        s.setId(3L);
        s.setEmail("diro@my.sliit.lk");
        s.setPassword(encoder.encode(rawPassword));
        return s;
    }

    @Test
    @DisplayName("A wrong current password is refused and nothing changes")
    void wrongCurrentPasswordIsRefused() {
        Student s = student("Secret123");
        String before = s.getPassword();
        when(appUserRepository.findByEmail("diro@my.sliit.lk")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.changePassword("diro@my.sliit.lk", "sess-1",
                new PasswordChangeRequest("Guess1234", "NewSecret456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incorrect");
        assertThat(s.getPassword()).isEqualTo(before);
        verify(sessionRevoker, never()).revokeOtherSessions(anyString(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // "Changing" to the same password passes every rule and secures nothing.
    @Test
    @DisplayName("Changing to the same password is refused")
    void samePasswordIsRefused() {
        when(appUserRepository.findByEmail(any())).thenReturn(Optional.of(student("Secret123")));

        assertThatThrownBy(() -> service.changePassword("diro@my.sliit.lk", "sess-1",
                new PasswordChangeRequest("Secret123", "Secret123")))
                .hasMessageContaining("different");
    }

    @Test
    @DisplayName("A successful change stores a new hash, logs it, and ends the other sessions only")
    void successfulChange() {
        Student s = student("Secret123");
        when(appUserRepository.findByEmail("diro@my.sliit.lk")).thenReturn(Optional.of(s));

        service.changePassword("diro@my.sliit.lk", "sess-1",
                new PasswordChangeRequest("Secret123", "NewSecret456"));

        assertThat(encoder.matches("NewSecret456", s.getPassword())).isTrue();
        assertThat(encoder.matches("Secret123", s.getPassword())).isFalse();
        verify(activityLogService).record(eq(3L), eq(ActivityType.PASSWORD_CHANGED), anyString());
        verify(sessionRevoker).revokeOtherSessions("diro@my.sliit.lk", "sess-1");
        // Observer: the change is announced so the user gets a security notification.
        verify(eventPublisher).publishEvent(new PasswordChangedEvent(3L));
    }

    // Officers have no activity log; the change must still work for them.
    @Test
    @DisplayName("An officer can change their password too")
    void officerCanChangePassword() {
        Officer o = new Officer("officer@helpdesk.local", encoder.encode("Officer123"),
                "OF20250009", "Support Officer", "Test Officer");
        when(appUserRepository.findByEmail("officer@helpdesk.local")).thenReturn(Optional.<AppUser>of(o));

        service.changePassword("officer@helpdesk.local", null,
                new PasswordChangeRequest("Officer123", "OfficerNew456"));

        assertThat(encoder.matches("OfficerNew456", o.getPassword())).isTrue();
        verify(activityLogService, never()).record(any(), any(), any());
    }
}
