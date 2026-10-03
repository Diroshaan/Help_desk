package com.helpdesk.queue.dto;

import com.helpdesk.common.user.entity.Officer;

/** F4 - an officer and who supervises them (no supervisor is a normal state, so both may be null). */
public record OfficerSupervisorResponse(Long officerId, Long supervisorId, String supervisorName) {

    public static OfficerSupervisorResponse from(Officer officer) {
        Officer supervisor = officer.getSupervisor();
        return new OfficerSupervisorResponse(officer.getId(),
                supervisor == null ? null : supervisor.getId(),
                supervisor == null ? null : supervisor.getFullName());
    }
}
