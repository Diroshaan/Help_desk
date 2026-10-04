package com.helpdesk.profile.dto;

import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.user.entity.Officer;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * An officer's own profile page. email, staffNumber, jobTitle and departments are shown
 * but read-only: departments decide which tickets an officer can see, so letting them
 * edit it would be a privilege escalation. Only an admin changes those.
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

    public record DepartmentSummary(String code, String name) {
        static DepartmentSummary from(Department d) {
            return new DepartmentSummary(d.getCode(), d.getName());
        }
    }

    /**
     * Call inside a transaction, since departments is lazy.
     * Sorted by code so the order doesn't change between refreshes.
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
