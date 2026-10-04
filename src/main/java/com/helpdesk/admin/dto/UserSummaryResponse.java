package com.helpdesk.admin.dto;

import com.helpdesk.common.reference.entity.Department;
import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.profile.entity.Student;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One row in the admin account listing, for students, officers and administrators.
 * It has no password field, so a hash can't leak even if an annotation goes missing.
 * label/reference mean name/student ID for students and name/staff number for staff.
 * departmentCodes is null for non-officers and may be empty for an officer with no desk,
 * which the screen needs to show. removed separates a removed account from a suspended one.
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
     * Builds a row for any account type. Reads lazy associations (provisionedBy,
     * departments), so it must be called inside a transaction. This means one extra
     * query per staff account, which is fine for a small admin list.
     */
    public static UserSummaryResponse from(AppUser user) {
        String label = "";
        String reference = "";

        // Students register themselves, so nobody provisioned them.
        String provisionedBy = null;

        // null means "not an officer"
        List<String> departmentCodes = null;

        if (user instanceof Student student) {
            label = student.getFullName();
            reference = student.getStudentId();
        } else if (user instanceof Officer officer) {
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

    // null if nobody is on record; we don't invent a placeholder name.
    private static String nameOf(Administrator provisioner) {
        return provisioner == null ? null : provisioner.getDisplayName();
    }

    // Sorted so the same officer always comes back in the same order.
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