package com.helpdesk.admin.dto;

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
 */
public record UserSummaryResponse(
        Long id,
        String email,
        String role,
        boolean active,
        LocalDateTime createdAt,
        String label,
        String reference
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
     */
    public static UserSummaryResponse from(AppUser user) {
        String label = "";
        String reference = "";

        if (user instanceof Student student) {
            label = student.getFullName();
            reference = student.getStudentId();
        } else if (user instanceof Officer officer) {
            label = officer.getJobTitle();
            reference = officer.getStaffNumber();
        } else if (user instanceof Administrator administrator) {
            label = administrator.getDisplayName();
            reference = administrator.getStaffNumber() == null ? "" : administrator.getStaffNumber();
        }

        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt(),
                label,
                reference
        );
    }

    public static List<UserSummaryResponse> fromAll(List<? extends AppUser> users) {
        return users.stream().map(UserSummaryResponse::from).toList();
    }
}
