package com.helpdesk.common.reference.dto;

import com.helpdesk.common.reference.entity.Department;

import java.util.List;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * What a department looks like when it leaves the API.
 *
 * Same reasoning as StudentResponse in F1: the entity is never serialised
 * straight to the client. Here there is no password to protect, so the argument
 * is about coupling rather than secrecy - if the JSON is produced directly from
 * the entity, then every field anyone adds to Department silently becomes part
 * of the public API, and the frontend quietly starts depending on it. A DTO
 * makes the wire format something decided on purpose. The 'active' flag, for
 * instance, is deliberately absent: the endpoint only ever returns active
 * departments, so telling the client active=true on every row is noise that
 * invites it to write a filter it does not need.
 *
 * A Java record rather than a class: this is a pure carrier of values with no
 * behaviour, which is exactly what records are for. The compiler writes the
 * constructor, the accessors, equals, hashCode and toString, so there is no
 * hand-written boilerplate to get wrong, and the fields are final so a response
 * cannot be altered after it is built.
 */
public record DepartmentResponse(
        String code,
        String name,
        String contactEmail,
        String contactPhone
) {

    /**
     * The one place that knows how to turn a Department into its response form.
     *
     * A static factory on the DTO, not a mapper class and not logic inside the
     * controller: every caller converts the same way, so a change to the wire
     * format is a change in exactly one file.
     */
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
