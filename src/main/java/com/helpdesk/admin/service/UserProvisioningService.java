package com.helpdesk.admin.service;

import com.helpdesk.admin.dto.ProvisionAdministratorRequest;
import com.helpdesk.admin.dto.ProvisionOfficerRequest;
import com.helpdesk.admin.dto.UserSummaryResponse;
import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
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

import java.util.List;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Creating privileged accounts and suspending or restoring existing ones
 * (WBHD-35).
 *
 * Requirement specification 3.1: "Provision privileged staff/admin accounts and
 * assign role-based permissions; deactivate or delete student, help desk or
 * other admin accounts."
 *
 *
 * WHY PROVISIONING LIVES HERE AND NOT IN A SHARED PACKAGE
 * -------------------------------------------------------
 * common.user holds the entities and repositories because four features need
 * them. It deliberately holds no service and no controller: creating a
 * privileged account is an access-controlled administrative action, and putting
 * it in a shared package would mean any feature that imports the user model
 * also imports the ability to mint an officer. The shared package holds the
 * shape of a user; F6 holds the authority to create one.
 */
@Service
public class UserProvisioningService {

    private final AppUserRepository appUserRepository;
    private final OfficerRepository officerRepository;
    private final AdministratorRepository administratorRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserProvisioningService(AppUserRepository appUserRepository,
                                   OfficerRepository officerRepository,
                                   AdministratorRepository administratorRepository,
                                   PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.officerRepository = officerRepository;
        this.administratorRepository = administratorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------
    // Provisioning
    // ------------------------------------------------------------------

    /**
     * Create a help desk officer account.
     *
     * The email pre-check queries AppUserRepository, which spans the WHOLE
     * hierarchy, not OfficerRepository - because email is unique across every
     * account type. Checking only the officers table would let an officer be
     * created with a student's address, and the failure would arrive as a
     * database constraint violation that GlobalExceptionHandler turns into a
     * generic 409 with no mention of which field is at fault.
     *
     * Both pre-checks are exactly that - pre-checks. Two simultaneous requests
     * can both query before either saves, both find nothing, and one loses at
     * the database. The unique constraints are what GUARANTEE no duplicate, and
     * GlobalExceptionHandler's DataIntegrityViolationException handler is the
     * backstop. The checks here exist to produce a message that names the
     * problem in the normal case. Two layers answering the same question: one
     * fast and friendly, one slow and certain. StudentService.register makes the
     * same argument at more length.
     *
     * The password is hashed here and the plaintext is never stored, never
     * logged and never returned - UserSummaryResponse has no password field at
     * all.
     */
    @Transactional
    public UserSummaryResponse provisionOfficer(ProvisionOfficerRequest request) {
        rejectDuplicateEmail(request.email());

        if (officerRepository.existsByStaffNumber(request.staffNumber())) {
            throw new DuplicateResourceException(
                    "Staff number " + request.staffNumber() + " is already issued to another officer.");
        }

        Officer officer = new Officer(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.staffNumber(),
                request.jobTitle()
        );

        return UserSummaryResponse.from(officerRepository.save(officer));
    }

    /**
     * Create a system administrator account.
     *
     * staffNumber is optional, matching the entity - the first administrator in
     * a new deployment is a bootstrap account created before anybody has been
     * issued a number, and demanding one would mean inventing a fake. It stays
     * unique when supplied, which a SQL unique constraint expresses for free
     * because it permits multiple NULLs.
     *
     * Blank is normalised to null rather than stored. An empty string is a
     * VALUE as far as a unique constraint is concerned, so a second
     * administrator provisioned through a form that sends "" would be rejected
     * as a duplicate staff number - a confusing failure with no real cause.
     */
    @Transactional
    public UserSummaryResponse provisionAdministrator(ProvisionAdministratorRequest request) {
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

        return UserSummaryResponse.from(administratorRepository.save(administrator));
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    /**
     * Every account in the system, optionally narrowed to one role.
     *
     * The filter is applied in Java rather than as a repository query, and that
     * is a conscious trade rather than an oversight. getRole() is not a mapped
     * column - AppUser has no role column on purpose, because under JOINED the
     * table a row lives in IS its role (see the comment on AppUser) - so there
     * is nothing to put in a WHERE clause. Filtering by role in the database
     * would mean "SELECT ... FROM AppUser u WHERE TYPE(u) = Officer", which
     * works, but needs a Class parameter rather than the Role enum the API
     * takes, and a mapping between the two somewhere.
     *
     * The honest reason this is acceptable HERE and not in the dashboard queries
     * is size. This is a bounded administrative list of user accounts, read
     * occasionally by one administrator. The dashboard aggregates the tickets
     * table, which is unbounded and grows every day - that is where loading rows
     * to discard them in Java becomes the FeedbackService.summaryByCategory
     * mistake. If the account list ever needs paging, the TYPE(u) query is the
     * change to make, and this comment is the reason it was not made today.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> findAll(Role role) {
        List<AppUser> users = appUserRepository.findAll();
        if (role != null) {
            users = users.stream().filter(user -> user.getRole() == role).toList();
        }
        return UserSummaryResponse.fromAll(users);
    }

    // ------------------------------------------------------------------
    // Suspend, restore, soft delete
    // ------------------------------------------------------------------

    /**
     * Suspend or restore any account.
     *
     * ONE SAFETY RULE: THE SYSTEM MUST ALWAYS HAVE AN ACTIVE ADMINISTRATOR
     * -------------------------------------------------------------------
     * A deployment with no active administrator is locked out of itself.
     * Provisioning an administrator is an administrator action, so there is no
     * way to create a replacement through the application - the only way back
     * in is a manual UPDATE against the database.
     *
     * That single invariant is the whole rule, and it deliberately replaces the
     * two overlapping rules this method used to carry. The earlier version also
     * refused to let an administrator deactivate THEMSELVES, which sounds like
     * a separate protection but is not: "do not leave the system without an
     * administrator" already covers the only case where self-deactivation does
     * real harm. Worse, the self-check ran first and made the
     * last-administrator guard unreachable - any OTHER administrator doing the
     * deactivating is themselves still active, so the guard could never fire
     * through the API and could never be tested there. One invariant is easier
     * to defend at a viva than two that shadow each other, and this one is
     * reachable.
     *
     * The consequence, stated so it is a decision rather than an oversight: an
     * administrator MAY now deactivate their own account, provided another
     * active administrator remains. They lock themselves out; the system stays
     * administrable, which is the property that actually matters. If that ever
     * needs to be refused as well, it is a separate rule with a separate
     * message, not a reinterpretation of this one.
     *
     * WHY existsByActiveTrueAndIdNot AND NOT existsByActiveTrue
     * ---------------------------------------------------------
     * The question is "is there an active administrator OTHER than this one?",
     * asked while the target is still active. Plain existsByActiveTrue() would
     * count the account being deactivated and always answer yes, which is why
     * the earlier version had to flip the flag first, query, and rely on a
     * rollback to undo it. Excluding the target by id asks the real question
     * directly, before anything is written - no speculative write, no
     * dependence on flush ordering, and nothing to undo.
     *
     * The rule lives in the service rather than the controller because it is a
     * fact about the state of the system, not about HTTP. A seeder, a scheduled
     * job or a future bulk import gets it by calling this method, and cannot
     * route around it by using a different endpoint or verb.
     */
    @Transactional
    public UserSummaryResponse setActive(Long id, boolean active) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists with id " + id + "."));

        // Only deactivating an administrator can break the invariant. Restoring
        // an account never can, and a student or officer is not an
        // administrator, so neither case needs the query.
        if (!active && user.isActive() && user instanceof Administrator) {
            if (!administratorRepository.existsByActiveTrueAndIdNot(user.getId())) {
                throw new IllegalArgumentException(
                        "This is the last active administrator account. Provision another "
                                + "administrator before deactivating this one.");
            }
        }

        user.setActive(active);
        return UserSummaryResponse.from(appUserRepository.save(user));
    }

    /**
     * DELETE /api/admin/users/{id} - and it is a SOFT delete, always.
     *
     * Never a hard row delete. Tickets, bookmarks, feedback and activity-log
     * rows all carry user ids; removing the row destroys the history of every
     * ticket that person ever handled, and on the associations that ARE real
     * foreign keys the database would refuse the delete anyway. AppUser.active
     * is the mechanism and StudentService.deactivate() is the existing pattern -
     * a deactivated account cannot authenticate, because
     * StudentUserDetailsService builds the UserDetails with
     * .disabled(!isActive()).
     *
     * This delegates to setActive rather than repeating the flag flip, so the
     * last-administrator invariant applies to DELETE exactly as it does to
     * PATCH. An administrator must not be able to route around "you cannot
     * remove the last administrator" by choosing a different HTTP verb.
     */
    @Transactional
    public void softDelete(Long id) {
        setActive(id, false);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
