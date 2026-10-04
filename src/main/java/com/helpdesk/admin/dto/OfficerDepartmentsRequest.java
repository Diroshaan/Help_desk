package com.helpdesk.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Body for PUT /api/admin/officers/{id}/departments: the full set of desks an officer
 * serves. Sending the whole set keeps the call idempotent. It has no email or password
 * fields, so those can't be changed through it.
 */
public record OfficerDepartmentsRequest(

        @NotEmpty(message = "Choose at least one department for this officer")
        @Size(max = 10, message = "An officer can serve at most 10 departments")
        Set<String> departmentCodes
) {
}
