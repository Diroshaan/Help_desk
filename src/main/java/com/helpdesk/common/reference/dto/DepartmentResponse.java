package com.helpdesk.common.reference.dto;

import com.helpdesk.common.reference.entity.Department;

import java.util.List;

/**
 * Department for the API, so entity changes don't leak into the JSON. No 'active' field
 * because the endpoint only returns active departments.
 */
public record DepartmentResponse(
        String code,
        String name,
        String contactEmail,
        String contactPhone
) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getCode(),
                department.getName(),
                department.getContactEmail(),
                department.getContactPhone()
        );
    }

    public static List<DepartmentResponse> fromAll(List<Department> departments) {
        return departments.stream().map(DepartmentResponse::from).toList();
    }
}
