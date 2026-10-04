package com.helpdesk.admin.service;

import com.helpdesk.admin.dto.OfficerDepartmentsRequest;
import com.helpdesk.admin.dto.ProvisionAdministratorRequest;
import com.helpdesk.admin.dto.ProvisionOfficerRequest;
import com.helpdesk.admin.dto.UserSummaryResponse;
import com.helpdesk.admin.repository.AdministratorLockRepository;
import com.helpdesk.auth.SessionRevoker;
import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.reference.repository.DepartmentRepository;
import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.entity.Role;
import com.helpdesk.common.user.repository.AdministratorRepository;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.common.user.repository.OfficerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates officer and administrator accounts, edits officer departments, and suspends,
 * restores or removes accounts. Kept in the admin feature, not common.user, so only
 * admin code has the power to create privileged accounts.
 */
@Service
public class UserProvisioningService {

    private final AppUserRepository appUserRepository;
    private final OfficerRepository officerRepository;
    private final AdministratorRepository administratorRepository;
    private final PasswordEncoder passwordEncoder;

    // Signs a suspended or removed user out of sessions they already have open.
    private final SessionRevoker sessionRevoker;

    // Read only: used to check the department codes an admin picks.
    private final DepartmentRepository departmentRepository;

    private final AdministratorLockRepository administratorLockRepository;

    @Autowired
    public UserProvisioningService(AppUserRepository appUserRepository,
                                   OfficerRepository officerRepository,
                                   AdministratorRepository administratorRepository,
                                   PasswordEncoder passwordEncoder,
                                   SessionRevoker sessionRevoker,
                                   DepartmentRepository departmentRepository,
                                   AdministratorLockRepository administratorLockRepository) {
        this.appUserRepository = appUserRepository;
        this.officerRepository = officerRepository;
        this.administratorRepository = administratorRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevoker = sessionRevoker;
        this.departmentRepository = departmentRepository;
        this.administratorLockRepository = administratorLockRepository;
    }

    /**
     * Creates an officer. The email check covers every account type because emails are
     * unique across all of them. These checks give a clear message; the unique
     * constraints are the real guarantee if two requests race.
     */
    @Transactional
    public UserSummaryResponse provisionOfficer(ProvisionOfficerRequest request, String callerEmail) {
        // Check who is asking first, so a non-admin can't use this to find out which
        // emails are registered.
        Administrator provisioner = requireAdministrator(callerEmail);

        rejectDuplicateEmail(request.email());

        if (officerRepository.existsByStaffNumber(request.staffNumber())) {
            throw new DuplicateResourceException(
                    "Staff number " + request.staffNumber() + " is already issued to another officer.");
        }

        // Bad department codes fail here, before anything is saved.
        Set<Department> departments = resolveDepartments(request.departmentCodes());

        Officer officer = new Officer(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.staffNumber(),
                request.jobTitle(),
                request.fullName()
        );

        // provisionedBy is nullable in the DB, so set it before saving to keep the audit record.
        officer.setProvisionedBy(provisioner);
        officer.setDepartments(departments);

        return UserSummaryResponse.from(officerRepository.save(officer));
    }

    /**
     * Creates an administrator. A blank staff number is stored as null, because "" would
     * clash with the next blank one under the unique constraint. provisionedBy lets us
     * trace who created each admin.
     */
    @Transactional
    public UserSummaryResponse provisionAdministrator(ProvisionAdministratorRequest request, String callerEmail) {
        Administrator provisioner = requireAdministrator(callerEmail);

        rejectDuplicateEmail(request.email());

        String staffNumber = normaliseStaffNumber(request.staffNumber());
        if (staffNumber != null && administratorRepository.existsByStaffNumber(staffNumber)) {
            throw new DuplicateResourceException(
                    "Staff number " + staffNumber + " is already issued to another administrator.");
        }

        Administrator administrator = new Administrator(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName()
        );
        administrator.setStaffNumber(staffNumber);
        administrator.setProvisionedBy(provisioner);

        return UserSummaryResponse.from(administratorRepository.save(administrator));
    }

    /**
     * Replaces an officer's departments; also how older officers with no department get
     * fixed. Ids that aren't officers give 404. The set is changed in place so Hibernate
     * only writes the difference. Takes effect on the officer's next request.
     */
    @Transactional
    public UserSummaryResponse updateOfficerDepartments(Long officerId, OfficerDepartmentsRequest request) {
        Officer officer = officerRepository.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No officer account exists with id " + officerId + "."));

        // A removed officer can't be given desks again, or tickets could be routed to them.
        if (officer.isRemoved()) {
            throw new IllegalArgumentException(
                    "This officer account was removed, so its departments can no longer be changed.");
        }

        Set<Department> departments = resolveDepartments(request.departmentCodes());

        officer.getDepartments().clear();
        officer.getDepartments().addAll(departments);

        return UserSummaryResponse.from(officerRepository.save(officer));
    }

    /**
     * Filtered in Java because role isn't a column (the table a user lives in is their
     * role). Fine for a small admin list. Must stay in a transaction because
     * UserSummaryResponse.from reads lazy fields.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> findAll(Role role, boolean includeRemoved) {
        List<AppUser> users = appUserRepository.findAll();
        if (!includeRemoved) {
            users = users.stream().filter(user -> !user.isRemoved()).toList();
        }
        if (role != null) {
            users = users.stream().filter(user -> user.getRole() == role).toList();
        }
        return UserSummaryResponse.fromAll(users);
    }

    /**
     * Suspend or restore an account. Rules: you can't suspend yourself, there must
     * always be an active admin, and a removed account can't be restored. They live
     * here so no other endpoint can get around them.
     */
    @Transactional
    public UserSummaryResponse setActive(Long id, boolean active, String callerEmail) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists with id " + id + "."));

        if (!active && isCaller(user, callerEmail)) {
            throw new IllegalArgumentException("You can't deactivate your own account.");
        }
        if (active && user.isRemoved()) {
            throw new IllegalArgumentException("Removed accounts can't be restored.");
        }

        // Only taking away an active admin can leave the system with none.
        if (!active) {
            requireAnotherActiveAdmin(user);
        }

        user.setActive(active);
        UserSummaryResponse summary = UserSummaryResponse.from(appUserRepository.save(user));

        // Sign them out of open sessions, only when suspending and only after the save.
        if (!active) {
            sessionRevoker.revokeAllSessionsFor(user.getEmail());
        }

        return summary;
    }

    /**
     * Removes an account for good. It's a soft delete (deletedAt set, active false) so
     * tickets and logs still point at a real row. Same self and last-admin rules as
     * setActive.
     */
    @Transactional
    public void softDelete(Long id, String callerEmail) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists with id " + id + "."));

        if (isCaller(user, callerEmail)) {
            throw new IllegalArgumentException("You can't remove your own account.");
        }

        // Already removed: do nothing, so a retry doesn't fail or change deletedAt.
        if (user.isRemoved()) {
            return;
        }

        requireAnotherActiveAdmin(user);

        user.markRemoved();
        appUserRepository.save(user);
        sessionRevoker.revokeAllSessionsFor(user.getEmail());
    }

    // Compared by email (what the session holds), ignoring case.
    private boolean isCaller(AppUser target, String callerEmail) {
        return callerEmail != null && target.getEmail().equalsIgnoreCase(callerEmail);
    }

    /**
     * Stops the last active admin being suspended or removed. If admin A suspends B
     * while B suspends A, both could see "another admin exists", so we lock the active
     * admin rows first (pessimistic lock); the second request waits and then sees the
     * real state. We use the locked result itself, not a separate query, so we don't
     * read a stale snapshot.
     */
    private void requireAnotherActiveAdmin(AppUser user) {
        if (!(user instanceof Administrator) || !user.isActive()) {
            return;
        }
        boolean anotherActive = administratorLockRepository.lockActiveAdministrators().stream()
                .anyMatch(administrator -> !administrator.getId().equals(user.getId()));
        if (!anotherActive) {
            throw new IllegalArgumentException(
                    "This is the last active administrator account. Provision another "
                            + "administrator before deactivating or removing this one.");
        }
    }

    /**
     * Loads the caller through AppUserRepository so we get the real account type, then
     * checks it is an Administrator before storing it as provisionedBy.
     */
    private Administrator requireAdministrator(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists for the signed-in user."));

        if (!(user instanceof Administrator administrator)) {
            throw new IllegalArgumentException(
                    "Only an administrator can provision accounts.");
        }
        return administrator;
    }

    /**
     * Turns department codes into entities in one query. Unknown or closed departments
     * are rejected with a 400 that names the code, since a closed desk gets no tickets.
     */
    private Set<Department> resolveDepartments(Set<String> requestedCodes) {
        Set<String> codes = new HashSet<>();
        for (String code : requestedCodes) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Department codes must not be blank.");
            }
            codes.add(code.trim());
        }

        Map<String, Department> found = departmentRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(Department::getCode, Function.identity()));

        Set<Department> departments = new HashSet<>();
        for (String code : codes) {
            Department department = found.get(code);
            if (department == null) {
                throw new IllegalArgumentException("Unknown department code: " + code + ".");
            }
            if (!department.isActive()) {
                throw new IllegalArgumentException(
                        "Department " + code + " is closed and cannot be assigned to an officer.");
            }
            departments.add(department);
        }
        return departments;
    }

    private void rejectDuplicateEmail(String email) {
        if (appUserRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                    "An account already exists with the email address " + email + ".");
        }
    }

    private String normaliseStaffNumber(String staffNumber) {
        if (staffNumber == null || staffNumber.isBlank()) {
            return null;
        }
        return staffNumber.trim();
    }
}