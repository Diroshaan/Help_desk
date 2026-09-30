package com.helpdesk.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Request body for PUT /api/admin/officers/{id}/departments (F6-N3) - replacing
 * the set of departments an existing officer serves.
 *
 * WHY THIS ENDPOINT EXISTS AT ALL
 * -------------------------------
 * Adding departmentCodes to ProvisionOfficerRequest fixes every officer created
 * from now on. It does nothing for the officers that already exist with an
 * empty set, and nothing for the ordinary case of somebody moving desks. Before
 * this endpoint, the only code in the system that could assign a department to
 * an officer was the dev seeder - so on the shared database an empty officer
 * could never be repaired through the application.
 *
 * WHY PUT WITH THE WHOLE SET, NOT ADD/REMOVE ONE CODE AT A TIME
 * -------------------------------------------------------------
 * The screen shows every department as a checkbox and the administrator ticks
 * the ones this officer serves, so the natural message is "here is the complete
 * set". Replacing the set is idempotent - sending the same body twice leaves the
 * same result - and it cannot drift the way a sequence of add/remove calls can
 * when one of them fails halfway.
 *
 * A separate record from ProvisionOfficerRequest even though it holds one of the
 * same fields: this request must NOT carry an email, a password or a staff
 * number, and a DTO that has no field for a value cannot have that value forged
 * through it. Same anti-mass-assignment reasoning as the provisioning DTOs.
 *
 * The same rules as on provisioning, for the same reasons: at least one
 * department (an officer serving nothing is the bug being fixed), at most ten
 * (bound every list).
 */
public record OfficerDepartmentsRequest(

        @NotEmpty(message = "Choose at least one department for this officer")
        @Size(max = 10, message = "An officer can serve at most 10 departments")
        Set<String> departmentCodes
) {
}
