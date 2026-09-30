package com.helpdesk.admin.dto;

import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.profile.entity.Student;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * One row in the administrator's account listing, for any account type.
 *
 * NEVER RETURN Administrator, Officer OR AppUser FROM A CONTROLLER
 * ---------------------------------------------------------------
 * They all carry the BCrypt password hash. AppUser.password has
 * @JsonProperty(WRITE_ONLY), and that single annotation is the only thing
 * standing between the hash and the wire - delete it by accident and every
 * account listing leaks every hash in the system, with nothing failing to say
 * so. This class has no password field at all, so there is nothing to leak and
 * nothing to delete by mistake. Structural, not annotational. Same rule as
 * profile/dto/StudentResponse.java.
 *
 * ONE DTO FOR ALL THREE TYPES
 * ---------------------------
 * The admin listing shows students, officers and administrators in one table
 * with one set of columns, so one flat row type is the honest description of
 * that screen. 'label' and 'reference' are the two columns whose MEANING varies
 * by type - a student's name and registration number, an officer's job title
 * and staff number, an administrator's display name and staff number - and
 * from() below is the single place that knows the mapping. The alternative,
 * three response types and three endpoints, would push that decision into the
 * frontend and duplicate it there.
 *
 * WHY provisionedBy IS A NAME AND NOT AN Administrator
 * ----------------------------------------------------
 * Returning the entity would nest an AppUser subclass inside this response, and
 * AppUser is exactly what the section above says must never reach a controller.
 * Flattening it to a display name keeps the protection structural: there is no
 * path from this record to a password hash, whatever anybody annotates later.
 *
 * departmentCodes: A LIST FOR OFFICERS, null FOR EVERYONE ELSE (F6-N3)
 * -------------------------------------------------------------------
 * Only officers serve departments, so for a student or an administrator the
 * question does not apply and the value is null. For an officer it is always a
 * list - possibly an EMPTY one - and that difference is the point: an empty
 * list means "this officer serves nothing and cannot work routed tickets",
 * which is exactly the broken state F6-N3 exists to make visible and fixable.
 * If both cases were an empty list, the screen could not tell a student (fine)
 * from an officer nobody can route work to (a problem).
 *
 * removed AND removedAt
 * ---------------------
 * active = false alone does not say whether an account is SUSPENDED (can come
 * back) or REMOVED (final - AppUser.deletedAt, PR #53). The screen needs to
 * know which: a removed officer must not be offered the "Departments" editor,
 * and the service refuses that edit anyway. removed is the flag the screen
 * branches on; removedAt says when, for anyone asking who left and when.
 */
public record UserSummaryResponse(
        Long id,
        String email,
        String role,
        boolean active,
        LocalDateTime createdAt,
        String label,
        String reference,
        String provisionedBy,
        List<String> departmentCodes,
        boolean removed,
        LocalDateTime removedAt
) {

    /**
     * Builds a row from whichever account type this is.
     *
     * getRole() is abstract on AppUser, so the role can never disagree with the
     * table the row actually lives in - see the comment on AppUser for why there
     * is no role column to get out of step.
     *
     * instanceof pattern matching for the type-specific columns rather than a
     * method on each entity: those two columns exist for this screen and no
     * other, and putting a getLabelForAdminListing() on the shared user
     * entities would be F6 writing its own presentation concern into files three
     * other features depend on.
     *
     * The final else is not dead code. It is what a fourth account type gets on
     * the day somebody adds one - a row with blank type-specific columns rather
     * than a ClassCastException or a silently missing user.
     *
     * READING provisionedBy TOUCHES A LAZY ASSOCIATION
     * ------------------------------------------------
     * Officer.provisionedBy and Administrator.provisionedBy are LAZY @ManyToOne,
     * so nameOf() below dereferences a proxy. That is safe only inside an open
     * persistence context. Every caller of this method today is a @Transactional
     * method on UserProvisioningService, so it is fine - but it is fine by
     * circumstance rather than by construction, and moving a from() call outside
     * a transaction would turn it into a LazyInitializationException at runtime
     * with nothing failing at compile time to warn you.
     *
     * It is also an N+1: findAll() loads every account in one query, then this
     * fires one more per officer and administrator to resolve the provisioner.
     * Left as-is for the reason UserProvisioningService.findAll already argues at
     * length - this is a bounded administrative list read occasionally by one
     * person, not the unbounded tickets table. If the listing ever needs paging,
     * a fetch join is the change to make, and this comment is why it was not made
     * today.
     */
    public static UserSummaryResponse from(AppUser user) {
        String label = "";
        String reference = "";

        // Null, not "". Students are not provisioned by anybody - they register
        // themselves - so there is no name to show and none is missing. Note
        // that null here also covers accounts provisioned before this was
        // recorded at all: the API cannot distinguish "not applicable" from
        // "not known", and neither can the database. Both are truthfully
        // "no administrator is on record", which is what the client renders.
        String provisionedBy = null;

        // null = not applicable (not an officer). See the class comment.
        List<String> departmentCodes = null;

        if (user instanceof Student student) {
            label = student.getFullName();
            reference = student.getStudentId();
        } else if (user instanceof Officer officer) {
            // Was getJobTitle(), because Officer had no name field when this
            // screen was written. It does now, so the name column shows a name
            // for all three account types instead of showing "Support Officer"
            // for every officer on the list - a job title is neither unique nor
            // an identity, and two officers sharing one made the listing
            // ambiguous.
            label = officer.getFullName();
            reference = officer.getStaffNumber();
            provisionedBy = nameOf(officer.getProvisionedBy());
            departmentCodes = codesOf(officer);
        } else if (user instanceof Administrator administrator) {
            label = administrator.getDisplayName();
            reference = administrator.getStaffNumber() == null ? "" : administrator.getStaffNumber();
            provisionedBy = nameOf(administrator.getProvisionedBy());
        }

        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt(),
                label,
                reference,
                provisionedBy,
                departmentCodes,
                user.isRemoved(),
                user.getDeletedAt()
        );
    }

    /**
     * The provisioning administrator's display name, or null when none is
     * recorded.
     *
     * Null rather than "System" or "Unknown": inventing a placeholder here would
     * mean the API asserting something the database never said. The client
     * decides how to render an absent value; this class decides what is true.
     */
    private static String nameOf(Administrator provisioner) {
        return provisioner == null ? null : provisioner.getDisplayName();
    }

    /**
     * The codes of the departments this officer serves, sorted.
     *
     * Sorted so the same officer always serializes the same way - a HashSet has
     * no order, and a list that reshuffles between two requests makes the
     * screen flicker and makes a test assertion flaky for no real reason.
     *
     * Officer.departments is a LAZY @ManyToMany, so this is the same situation
     * as provisionedBy above: it works because every caller of from() runs
     * inside a @Transactional service method, and it costs one extra query per
     * officer in the listing. Same bounded-listing argument, same fix (a fetch
     * join) if the listing ever needs paging.
     */
    private static List<String> codesOf(Officer officer) {
        return officer.getDepartments().stream()
                .map(Department::getCode)
                .sorted()
                .toList();
    }

    public static List<UserSummaryResponse> fromAll(List<? extends AppUser> users) {
        return users.stream().map(UserSummaryResponse::from).toList();
    }
}