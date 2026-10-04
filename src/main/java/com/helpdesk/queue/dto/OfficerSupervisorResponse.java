package com.helpdesk.queue.dto;

import com.helpdesk.common.user.entity.Officer;

/** An officer and their supervisor; the supervisor fields are null if they have none. */
public record OfficerSupervisorResponse(Long officerId, Long supervisorId, String supervisorName) {

    public static OfficerSupervisorResponse from(Officer officer) {
        Officer supervisor = officer.getSupervisor();
        return new OfficerSupervisorResponse(officer.getId(),
                supervisor == null ? null : supervisor.getId(),
                supervisor == null ? null : supervisor.getFullName());
    }
}
