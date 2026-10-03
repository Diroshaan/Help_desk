package com.helpdesk.queue.dto;

/**
 * F4 - request body for PUT /api/admin/officers/{id}/supervisor.
 * A null supervisorId clears the officer's supervisor.
 */
public class SupervisorRequest {

    private Long supervisorId;

    public Long getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(Long supervisorId) {
        this.supervisorId = supervisorId;
    }
}
