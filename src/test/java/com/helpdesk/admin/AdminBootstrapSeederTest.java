package com.helpdesk.admin;

import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.AdministratorRepository;
import com.helpdesk.common.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The first-run administrator seeder when the bootstrap address already exists
 * (F6-N6 part 1).
 *
 * The bug: on a database that had run before, the bootstrap account is still
 * there but suspended. The seeder saw "no ACTIVE administrator", tried to insert
 * the same email again and hit the unique constraint, so the application could
 * not start. Mockito is enough here because what is under test is the decision
 * (insert, reactivate or leave alone), not the database.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapSeederTest {

    private static final String EMAIL = "admin@helpdesk.local";

    @Mock private AdministratorRepository administratorRepository;
    @Mock private AppUserRepository appUserRepository;

    private AdminBootstrapSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new AdminBootstrapSeeder(administratorRepository, appUserRepository,
                new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(seeder, "bootstrapEmail", EMAIL);
        ReflectionTestUtils.setField(seeder, "bootstrapDisplayName", "System Administrator");
        ReflectionTestUtils.setField(seeder, "bootstrapPassword", "");
    }

    @Test
    @DisplayName("an existing active administrator means the seeder does nothing")
    void doesNothingWhenAnAdministratorIsActive() {
        when(administratorRepository.existsByActiveTrue()).thenReturn(true);

        seeder.run(null);

        verify(administratorRepository, never()).save(any(Administrator.class));
    }

    @Test
    @DisplayName("a missing bootstrap account is inserted")
    void insertsWhenTheEmailDoesNotExist() {
        when(administratorRepository.existsByActiveTrue()).thenReturn(false);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        seeder.run(null);

        ArgumentCaptor<Administrator> saved = ArgumentCaptor.forClass(Administrator.class);
        verify(administratorRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("a suspended bootstrap account is reactivated with a new password, not inserted again")
    void reactivatesASuspendedBootstrapAccount() {
        Administrator suspended = new Administrator(EMAIL, "old-hash", "System Administrator");
        suspended.setActive(false);
        when(administratorRepository.existsByActiveTrue()).thenReturn(false);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(suspended));

        seeder.run(null);

        assertThat(suspended.isActive()).isTrue();
        assertThat(suspended.getPassword()).isNotEqualTo("old-hash");
        verify(administratorRepository).save(suspended);
    }

    @Test
    @DisplayName("a removed bootstrap account is not resurrected, and startup carries on")
    void doesNotRecreateARemovedBootstrapAccount() {
        Administrator removed = new Administrator(EMAIL, "old-hash", "System Administrator");
        removed.markRemoved();
        when(administratorRepository.existsByActiveTrue()).thenReturn(false);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(removed));

        seeder.run(null);

        assertThat(removed.isActive()).isFalse();
        assertThat(removed.isRemoved()).isTrue();
        verify(administratorRepository, never()).save(any(Administrator.class));
    }

    @Test
    @DisplayName("an address that belongs to a non-administrator is left alone")
    void leavesAnotherKindOfAccountAlone() {
        AppUser officer = new Officer(EMAIL, "hash", "OF-1", "Support Officer", "Someone");
        when(administratorRepository.existsByActiveTrue()).thenReturn(false);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(officer));

        seeder.run(null);

        verify(administratorRepository, never()).save(any(Administrator.class));
    }
}
