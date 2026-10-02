package com.helpdesk.admin.service;

import com.helpdesk.admin.dto.OfficerDepartmentsRequest;
import com.helpdesk.admin.dto.ProvisionAdministratorRequest;
import com.helpdesk.admin.dto.ProvisionOfficerRequest;
import com.helpdesk.admin.dto.UserSummaryResponse;
import com.helpdesk.auth.SessionRevoker;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.AppUser;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserProvisioningService with its collaborators mocked.
 *
 * F6-N2 (who provisioned the account) and F6-N3 (which departments an officer
 * serves).
 *
 * Mockito rather than a running application because what is under test is this
 * service's own decision - that the administrator who is signed in, and nobody
 * the request nominates, is recorded as the provisioner; that an officer is
 * never created serving a department that does not exist. A mock lets a test
 * state "the caller's email resolves to a student" or "that department code is
 * closed" in one line, which is awkward to build in a database and impossible to
 * reach through the API while SecurityConfig is doing its job.
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
    @Mock private DepartmentRepository departmentRepository;

    private UserProvisioningService service;
    private Administrator caller;
    private Department it;
    private Department registration;

    @BeforeEach
    void setUp() {
        service = new UserProvisioningService(appUserRepository, officerRepository,
                administratorRepository, new BCryptPasswordEncoder(4), sessionRevoker,
                departmentRepository);
        caller = new Administrator(CALLER, "irrelevant", "System Administrator");
        it = new Department("IT", "IT Services", null, null);
        registration = new Department("REG", "Registration", null, null);
    }

    private ProvisionOfficerRequest officerRequest(Set<String> departmentCodes) {
        return new ProvisionOfficerRequest(
                "officer.new@helpdesk.local",
                "Secret123",
                "OF-1001",
                "Support Officer",
                "New Officer",
                departmentCodes);
    }

    private ProvisionOfficerRequest officerRequest() {
        return officerRequest(Set.of("IT"));
    }

    private ProvisionAdministratorRequest administratorRequest() {
        return new ProvisionAdministratorRequest(
                "admin.new@helpdesk.local",
                "Secret123",
                "Second Administrator",
                null);
    }

    /** The checks provisionOfficer runs before it reaches the departments. */
    private void callerAndPayloadAreValid() {
        when(appUserRepository.findByEmail(CALLER)).thenReturn(Optional.of(caller));
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(officerRepository.existsByStaffNumber(anyString())).thenReturn(false);
    }

    // ---- F6-N2: provisionedBy is recorded ----

    // Why this test exists: the provisionedBy column and its foreign key existed
    // from the first version of F6, and nothing ever assigned them. Every
    // account provisioned through the admin screen recorded nothing about who
    // created it, and no test failed, because no test asked. This one asks.
    @Test
    @DisplayName("provisionOfficer records the signed-in administrator")
    void provisionOfficerRecordsTheCallingAdministrator() {
        callerAndPayloadAreValid();
        when(departmentRepository.findAllById(Set.of("IT"))).thenReturn(List.of(it));
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

    // ---- F6-N2: the caller must actually be an administrator ----

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

    // ---- F6-N3: officers are provisioned WITH departments ----

    // Why this test exists: before F6-N3 the provisioning request had no
    // departments at all, so every officer created here served nothing and the
    // queue showed them no routed work. The saved officer must carry exactly the
    // departments the administrator picked.
    @Test
    @DisplayName("provisionOfficer assigns the departments the administrator chose")
    void provisionOfficerAssignsTheChosenDepartments() {
        callerAndPayloadAreValid();
        when(departmentRepository.findAllById(Set.of("IT", "REG"))).thenReturn(List.of(it, registration));
        when(officerRepository.save(any(Officer.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.provisionOfficer(officerRequest(Set.of("IT", "REG")), CALLER);

        ArgumentCaptor<Officer> saved = ArgumentCaptor.forClass(Officer.class);
        verify(officerRepository).save(saved.capture());
        assertThat(saved.getValue().getDepartments()).containsExactlyInAnyOrder(it, registration);

        // Sorted in the response, so the listing never reshuffles.
        assertThat(response.departmentCodes()).containsExactly("IT", "REG");
    }

    @Test
    @DisplayName("an unknown department code is refused by name, and nothing is saved")
    void provisionOfficerRejectsAnUnknownDepartment() {
        callerAndPayloadAreValid();
        when(departmentRepository.findAllById(Set.of("ITT"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.provisionOfficer(officerRequest(Set.of("ITT")), CALLER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ITT");

        verify(officerRepository, never()).save(any(Officer.class));
    }

    // A closed desk is refused too: assigning a new officer to it would rebuild
    // the original bug in a subtler form - an officer who serves "a department"
    // and still sees no work, because nothing is routed to a closed desk.
    @Test
    @DisplayName("a closed department is refused, and nothing is saved")
    void provisionOfficerRejectsAClosedDepartment() {
        callerAndPayloadAreValid();
        Department closed = new Department("OLD", "Old Desk", null, null);
        closed.setActive(false);
        when(departmentRepository.findAllById(Set.of("OLD"))).thenReturn(List.of(closed));

        assertThatThrownBy(() -> service.provisionOfficer(officerRequest(Set.of("OLD")), CALLER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OLD");

        verify(officerRepository, never()).save(any(Officer.class));
    }

    @Test
    @DisplayName("updateOfficerDepartments replaces the set, rather than adding to it")
    void updateOfficerDepartmentsReplacesTheSet() {
        Officer officer = new Officer("officer@helpdesk.local", "hash", "OF-7", "Support Officer", "An Officer");
        officer.getDepartments().add(it);
        when(officerRepository.findById(7L)).thenReturn(Optional.of(officer));
        when(departmentRepository.findAllById(Set.of("REG"))).thenReturn(List.of(registration));
        when(officerRepository.save(officer)).thenReturn(officer);

        var response = service.updateOfficerDepartments(7L, new OfficerDepartmentsRequest(Set.of("REG")));

        // IT is gone, not kept alongside REG: the request is the complete set.
        assertThat(officer.getDepartments()).containsExactly(registration);
        assertThat(response.departmentCodes()).containsExactly("REG");
    }

    // 404 for an id that is not an officer - including one that belongs to a
    // student or an administrator. OfficerRepository.findById only finds rows
    // in the officers table, so a student's id is simply "no such officer".
    @Test
    @DisplayName("updateOfficerDepartments on an id that is not an officer is 'not found'")
    void updateOfficerDepartmentsOnAnUnknownOfficerIsNotFound() {
        when(officerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateOfficerDepartments(99L,
                new OfficerDepartmentsRequest(Set.of("IT"))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(officerRepository, never()).save(any(Officer.class));
    }

    // Review on PR #56: a removed officer has left. Their record is kept for
    // history, not edited - and giving them desks would route tickets to
    // somebody who can no longer sign in.
    @Test
    @DisplayName("updateOfficerDepartments refuses a removed officer, and nothing is saved")
    void updateOfficerDepartmentsRefusesARemovedOfficer() {
        Officer officer = new Officer("gone@helpdesk.local", "hash", "OF-8", "Support Officer", "Gone Officer");
        officer.markRemoved();
        when(officerRepository.findById(8L)).thenReturn(Optional.of(officer));

        assertThatThrownBy(() -> service.updateOfficerDepartments(8L,
                new OfficerDepartmentsRequest(Set.of("IT"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("removed");

        verify(officerRepository, never()).save(any(Officer.class));
        verify(departmentRepository, never()).findAllById(any());
    }

    // ---- #48 / F6-N6 part 2: removal is final, and admins cannot lock themselves out ----

    private Officer officerWithId(long id, String email) {
        Officer officer = new Officer(email, "hash", "OF-" + id, "Support Officer", "Officer " + id);
        officer.setId(id);
        return officer;
    }

    private Administrator administratorWithId(long id, String email) {
        Administrator administrator = new Administrator(email, "hash", "Administrator " + id);
        administrator.setId(id);
        return administrator;
    }

    @Test
    @DisplayName("removing an account marks it removed and ends its sessions")
    void removingAnAccountIsFinalAndRevokesSessions() {
        Officer officer = officerWithId(5L, "leaving@helpdesk.local");
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(officer));

        service.softDelete(5L, CALLER);

        assertThat(officer.isRemoved()).isTrue();
        assertThat(officer.isActive()).isFalse();
        verify(appUserRepository).save(officer);
        verify(sessionRevoker).revokeAllSessionsFor("leaving@helpdesk.local");
    }

    @Test
    @DisplayName("restoring a removed account is refused, and nothing is saved")
    void restoringARemovedAccountIsRefused() {
        Officer officer = officerWithId(5L, "gone@helpdesk.local");
        officer.markRemoved();
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(officer));

        assertThatThrownBy(() -> service.setActive(5L, true, CALLER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can't be restored");

        assertThat(officer.isActive()).isFalse();
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    @DisplayName("an administrator cannot remove their own account")
    void removingYourselfIsRefused() {
        Administrator self = administratorWithId(1L, CALLER);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.softDelete(1L, CALLER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own account");

        assertThat(self.isRemoved()).isFalse();
        verify(appUserRepository, never()).save(any(AppUser.class));
        verify(sessionRevoker, never()).revokeAllSessionsFor(anyString());
    }

    @Test
    @DisplayName("an administrator cannot suspend their own account, whatever the case of the email")
    void suspendingYourselfIsRefused() {
        Administrator self = administratorWithId(1L, CALLER);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.setActive(1L, false, CALLER.toUpperCase()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own account");

        assertThat(self.isActive()).isTrue();
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    @DisplayName("the last active administrator can be neither removed nor suspended")
    void theLastActiveAdministratorIsProtected() {
        Administrator last = administratorWithId(2L, "last@helpdesk.local");
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(last));
        when(administratorRepository.existsByActiveTrueAndIdNot(2L)).thenReturn(false);

        assertThatThrownBy(() -> service.softDelete(2L, CALLER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last active administrator");
        assertThatThrownBy(() -> service.setActive(2L, false, CALLER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last active administrator");

        assertThat(last.isActive()).isTrue();
        assertThat(last.isRemoved()).isFalse();
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    @DisplayName("an administrator can be removed while another active one remains")
    void anAdministratorCanBeRemovedWhenAnotherRemains() {
        Administrator other = administratorWithId(2L, "other@helpdesk.local");
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(other));
        when(administratorRepository.existsByActiveTrueAndIdNot(2L)).thenReturn(true);

        service.softDelete(2L, CALLER);

        assertThat(other.isRemoved()).isTrue();
        verify(sessionRevoker).revokeAllSessionsFor("other@helpdesk.local");
    }

    @Test
    @DisplayName("removing an already removed account does nothing, and keeps the original date")
    void removingTwiceIsANoOp() {
        Officer officer = officerWithId(5L, "gone@helpdesk.local");
        officer.markRemoved();
        var originalDate = officer.getDeletedAt();
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(officer));

        service.softDelete(5L, CALLER);

        assertThat(officer.getDeletedAt()).isEqualTo(originalDate);
        verify(appUserRepository, never()).save(any(AppUser.class));
        verify(sessionRevoker, never()).revokeAllSessionsFor(anyString());
    }

    @Test
    @DisplayName("suspending somebody else still works and ends their sessions")
    void suspendingAnotherAccountStillWorks() {
        Officer officer = officerWithId(5L, "suspended@helpdesk.local");
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(officer));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(returnsFirstArg());

        UserSummaryResponse summary = service.setActive(5L, false, CALLER);

        assertThat(summary.active()).isFalse();
        assertThat(summary.removed()).isFalse();
        verify(sessionRevoker).revokeAllSessionsFor("suspended@helpdesk.local");
    }

    @Test
    @DisplayName("the listing hides removed accounts unless asked for them")
    void listingHidesRemovedAccountsByDefault() {
        Officer active = officerWithId(5L, "active@helpdesk.local");
        Officer removed = officerWithId(6L, "removed@helpdesk.local");
        removed.markRemoved();
        when(appUserRepository.findAll()).thenReturn(List.of(active, removed));

        assertThat(service.findAll(null, false)).extracting(UserSummaryResponse::id)
                .containsExactly(5L);
        assertThat(service.findAll(null, true)).extracting(UserSummaryResponse::id)
                .containsExactly(5L, 6L);
    }
}
