package com.helpdesk.queue.dto;

/** A null supervisorId clears the officer's supervisor. */
public class SupervisorRequest {

    private Long supervisorId;

    public Long getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(Long supervisorId) {
        this.supervisorId = supervisorId;
    }
}
