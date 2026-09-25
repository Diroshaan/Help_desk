package com.helpdesk.profile.dto;

import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.user.entity.Officer;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * What an officer sees on their own profile page (US-04).
 *
 * Two kinds of field, and the split is the point of the design:
 *
 *   Editable by the officer   fullName, phone, the two notification toggles
 *   Shown but NOT editable    email, staffNumber, jobTitle, departments
 *
 * The second group is governance data. An officer's staff number and job title
 * are issued by the university; the departments they serve decide which
 * tickets they can see (F4's queue scoping reads them). If an officer could
 * edit their own departments, they could grant themselves access to another
 * department's tickets - the profile page would become a privilege escalation.
 * Email is the login identity and unique across every account, so changing it
 * belongs with the administrator who provisioned it, not with a profile form.
 * They are returned so the page can SHOW them; OfficerProfileUpdateRequest has
 * no fields for them, so they cannot be changed through this endpoint at all.
 *
 * A record, not a class with getters like StudentResponse: this DTO is new, has
 * no history of partial construction, and a record states "immutable data
 * carrier" in one line. "phone" is the component name so the JSON matches the
 * student profile's field for the same thing.
 */
public record OfficerProfileResponse(
        Long id,
        String email,
        String fullName,
        String staffNumber,
        String jobTitle,
        List<DepartmentSummary> departments,
        String phone,
        boolean emailNotificationsEnabled,
        boolean portalNotificationsEnabled,
        boolean active,
        LocalDateTime createdAt
) {

    /** Code and name only - the page needs to show them, nothing more. */
    public record DepartmentSummary(String code, String name) {
        static DepartmentSummary from(Department d) {
            return new DepartmentSummary(d.getCode(), d.getName());
        }
    }

    /**
     * Must be called inside a transaction: departments is a lazy collection.
     * OfficerProfileService builds this before its @Transactional method
     * returns, so the page never depends on the open-session-in-view setting.
     *
     * Sorted by code so the list is stable between requests - a HashSet has no
     * order, and a list that reshuffles on every refresh looks like a bug.
     */
    public static OfficerProfileResponse from(Officer officer) {
        List<DepartmentSummary> departments = officer.getDepartments().stream()
                .map(DepartmentSummary::from)
                .sorted(Comparator.comparing(DepartmentSummary::code))
                .toList();
        return new OfficerProfileResponse(
                officer.getId(),
                officer.getEmail(),
                officer.getFullName(),
                officer.getStaffNumber(),
                officer.getJobTitle(),
                departments,
                officer.getContactNumber(),
                officer.isEmailNotificationsEnabled(),
                officer.isPortalNotificationsEnabled(),
                officer.isActive(),
                officer.getCreatedAt());
    }
}
