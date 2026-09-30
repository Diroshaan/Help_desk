package com.helpdesk.admin.service;

import com.helpdesk.admin.dto.ProvisionAdministratorRequest;
import com.helpdesk.admin.dto.ProvisionOfficerRequest;
import com.helpdesk.auth.SessionRevoker;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.AdministratorRepository;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.profile.entity.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserProvisioningService with its collaborators mocked (F6-N2).
 *
 * Mockito rather than a running application because what is under test is this
 * service's own decision - that the administrator who is signed in, and nobody
 * the request nominates, is recorded as the provisioner. A mock lets a test
 * state "the caller's email resolves to a student" in one line, which is a
 * situation that is awkward to build in a database and impossible to reach
 * through the API while SecurityConfig is doing its job.
 *
 * The real BCrypt encoder at its lowest strength, as StudentServiceTest does:
 * these tests never assert on the hash, but a mocked encoder returning null
 * would hide a NullPointerException that a real one would surface.
 */
@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    private static final String CALLER = "admin@helpdesk.local";

    @Mock private AppUserRepository appUserRepository;
    @Mock private OfficerRepository officerRepository;
    @Mock private AdministratorRepository administratorRepository;
    @Mock private SessionRevoker sessionRevoker;

    private UserProvisioningService service;
    private Administrator caller;

    @BeforeEach
    void setUp() {
        service = new UserProvisioningService(appUserRepository, officerRepository,
                administratorRepository, new BCryptPasswordEncoder(4), sessionRevoker);
        caller = new Administrator(CALLER, "irrelevant", "System Administrator");
    }

    private ProvisionOfficerRequest officerRequest() {
        return new ProvisionOfficerRequest(
                "officer.new@helpdesk.local",
                "Secret123",
                "OF-1001",
                "Support Officer",
                "New Officer");
    }

    private ProvisionAdministratorRequest administratorRequest() {
        return new ProvisionAdministratorRequest(
                "admin.new@helpdesk.local",
                "Secret123",
                "Second Administrator",
                null);
    }

    // ---- provisionedBy is recorded ----

    // Why this test exists: the provisionedBy column and its foreign key existed
    // from the first version of F6, and nothing ever assigned them. Every
    // account provisioned through the admin screen recorded nothing about who
    // created it, and no test failed, because no test asked. This one asks.
    @Test
    @DisplayName("provisionOfficer records the signed-in administrator")
    void provisionOfficerRecordsTheCallingAdministrator() {
        when(appUserRepository.findByEmail(CALLER)).thenReturn(Optional.of(caller));
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(officerRepository.existsByStaffNumber(anyString())).thenReturn(false);
        when(officerRepository.save(any(Officer.class))).thenAnswer(call -> call.getArgument(0));

        service.provisionOfficer(officerRequest(), CALLER);

        ArgumentCaptor<Officer> saved = ArgumentCaptor.forClass(Officer.class);
        verify(officerRepository).save(saved.capture());

        // isSameAs, not isEqualTo: the association must hold the administrator
        // entity the service resolved, not an equal-looking copy. A copy would
        // save a detached instance and, depending on the persistence context,
        // either fail at flush or silently insert a second administrator row.
        assertThat(saved.getValue().getProvisionedBy()).isSameAs(caller);
    }

    @Test
    @DisplayName("provisionAdministrator records the signed-in administrator")
    void provisionAdministratorRecordsTheCallingAdministrator() {
        when(appUserRepository.findByEmail(CALLER)).thenReturn(Optional.of(caller));
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(administratorRepository.save(any(Administrator.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.provisionAdministrator(administratorRequest(), CALLER);

        ArgumentCaptor<Administrator> saved = ArgumentCaptor.forClass(Administrator.class);
        verify(administratorRepository).save(saved.capture());

        assertThat(saved.getValue().getProvisionedBy()).isSameAs(caller);
    }

    // ---- the caller must actually be an administrator ----

    // SecurityConfig restricts /api/admin/** to ROLE_ADMIN, so this situation
    // should be unreachable through the API. The test exists because
    // "unreachable" is a claim about today's configuration, and the column being
    // written is declared Administrator. Without the type check the failure would
    // be a ClassCastException from inside Hibernate at flush time, which names
    // neither the caller nor the cause.
    @Test
    @DisplayName("a caller who is not an administrator is refused, and nothing is saved")
    void provisioningRejectsANonAdministratorCaller() {
        when(appUserRepository.findByEmail("student@my.sliit.lk"))
                .thenReturn(Optional.of(new Student()));

        assertThatThrownBy(() -> service.provisionOfficer(officerRequest(), "student@my.sliit.lk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administrator");

        verify(officerRepository, never()).save(any(Officer.class));

        // And the request was never inspected. This is the ordering decision in
        // provisionOfficer made testable: a caller we have just refused does not
        // get told, via the duplicate-email message, whether an address is
        // already registered. Delete the reordering and this line fails.
        verify(appUserRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("a caller whose email resolves to no account is refused")
    void provisioningRejectsAnUnknownCaller() {
        when(appUserRepository.findByEmail("ghost@helpdesk.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provisionOfficer(officerRequest(), "ghost@helpdesk.local"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(officerRepository, never()).save(any(Officer.class));
    }
}