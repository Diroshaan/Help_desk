package com.helpdesk.profile.service;

import com.helpdesk.auth.SessionRevoker;
import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.profile.dto.ProfileUpdateRequest;
import com.helpdesk.profile.dto.RegistrationRequest;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** StudentService with mocks and a real BCrypt encoder: registration, profile update, deactivation, avatars. */
@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private ActivityLogService activityLogService;
    @Mock private SessionRevoker sessionRevoker;

    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(studentRepository, new BCryptPasswordEncoder(4),
                activityLogService, sessionRevoker);
    }

    private RegistrationRequest registration() {
        RegistrationRequest r = new RegistrationRequest();
        r.setStudentId("IT25101580");
        r.setFullName("Diro Ruban");
        r.setEmail("diro@my.sliit.lk");
        r.setPassword("Secret123");
        r.setDepartment("Faculty of Computing");
        r.setContactNumber("0771234567");
        return r;
    }

    // register() forces id = null, so a save can never overwrite an existing account.
    @Test
    @DisplayName("Registration always inserts a new row, never updates an existing one")
    void registerForcesANewRow() {
        when(studentRepository.findByStudentId(any())).thenReturn(Optional.empty());
        when(studentRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        Student saved = service.register(registration());

        assertThat(saved.getId()).isNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPassword()).isNotEqualTo("Secret123").startsWith("$2");
    }

    @Test
    @DisplayName("Registration stores the name as two parts and the phone as the first contact number")
    void registerSplitsNameAndStoresPhone() {
        when(studentRepository.findByStudentId(any())).thenReturn(Optional.empty());
        when(studentRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        Student saved = service.register(registration());

        assertThat(saved.getGivenName()).isEqualTo("Diro");
        assertThat(saved.getSurname()).isEqualTo("Ruban");
        assertThat(saved.getContactNumbers()).containsExactly("0771234567");
    }

    @Test
    @DisplayName("A Student ID already in use is refused with a duplicate error")
    void duplicateStudentIdIsRefused() {
        Student existing = new Student();
        existing.setActive(true);
        when(studentRepository.findByStudentId("IT25101580")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(registration()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered");
        verify(studentRepository, never()).save(any());
    }

    // A closed account still holds its Student ID, so "log in instead" would be wrong.
    @Test
    @DisplayName("A Student ID belonging to a closed account gets a message that says so")
    void deactivatedAccountGetsItsOwnMessage() {
        Student closed = new Student();
        closed.setActive(false);
        when(studentRepository.findByStudentId("IT25101580")).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.register(registration()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    @DisplayName("A Student ID belonging to a closed (removed) account is not promised a restore")
    void removedAccountIsNotPromisedARestore() {
        Student closed = new Student();
        closed.markRemoved();
        when(studentRepository.findByStudentId("IT25101580")).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.register(registration()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("was closed")
                .hasMessageNotContaining("restored");
    }

    // "Save preferences" sends only the two booleans; that must not wipe the name or phone.
    @Test
    @DisplayName("A partial update leaves every field it did not mention untouched")
    void partialUpdateLeavesOtherFieldsAlone() {
        Student s = new Student();
        s.setFullName("Diro Ruban");
        s.setContactNumbers(List.of("0771234567", "0112345678"));
        s.setDepartment("Faculty of Computing");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(s));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileUpdateRequest prefsOnly = new ProfileUpdateRequest();
        prefsOnly.setEmailNotificationsEnabled(false);
        Student saved = service.updateProfile(1L, prefsOnly);

        assertThat(saved.getFullName()).isEqualTo("Diro Ruban");
        assertThat(saved.getContactNumbers()).containsExactly("0771234567", "0112345678");
        assertThat(saved.getDepartment()).isEqualTo("Faculty of Computing");
        assertThat(saved.isEmailNotificationsEnabled()).isFalse();
    }

    @Test
    @DisplayName("Sending only a surname changes only the surname")
    void surnameOnlyUpdate() {
        Student s = new Student();
        s.setFullName("Diro Ruban");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(s));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileUpdateRequest r = new ProfileUpdateRequest();
        r.setSurname("Sivakumar");
        Student saved = service.updateProfile(1L, r);

        assertThat(saved.getFullName()).isEqualTo("Diro Sivakumar");
    }

    @Test
    @DisplayName("Closing an account soft-deletes it and ends its live sessions")
    void deactivateSoftDeletesAndRevokes() {
        Student s = new Student();
        s.setEmail("diro@my.sliit.lk");
        s.setActive(true);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(s));

        service.deactivate(1L);

        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
        // Removed, not merely suspended: the admin screen must not offer it back.
        assertThat(saved.getValue().isRemoved()).isTrue();
        assertThat(saved.getValue().getDeletedAt()).isNotNull();
        verify(studentRepository, never()).delete(any());
        verify(sessionRevoker).revokeAllSessionsFor("diro@my.sliit.lk");
    }

    // The name and declared type come from the uploader; only the bytes count.
    @Test
    @DisplayName("A text file renamed to .png is rejected by its content, not its name")
    void avatarRejectsTextDisguisedAsPng() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(new Student()));
        MockMultipartFile fake = new MockMultipartFile("file", "photo.png", "image/png",
                "just some text pretending to be an image".getBytes());

        assertThatThrownBy(() -> service.updateAvatar(1L, fake))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid image");
        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A JPEG declared as PNG is stored as JPEG, with a cache-busting URL")
    void avatarStoresDetectedTypeAndVersionedUrl() {
        Student s = new Student();
        s.setId(7L);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(s));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
        MockMultipartFile jpeg = new MockMultipartFile("file", "me.png", "image/png", jpegHeader);

        Student saved = service.updateAvatar(7L, jpeg);

        assertThat(saved.getProfilePictureType()).isEqualTo("image/jpeg");
        assertThat(saved.getProfilePictureUrl()).startsWith("/api/students/7/avatar?v=");
    }
}
