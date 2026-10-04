package com.helpdesk.queue.dto;

/**
 * Body for routing or reassigning a ticket. Either field may be left out;
 * QueueService.assignTicket checks the combination makes sense.
 */
public class TicketAssignmentRequest {

    private String departmentId;

    private Long officerId;

    public String getDepartmentId() {
        return departmentId;
    }
    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }
    public Long getOfficerId() {
        return officerId;
    }
    public void setOfficerId(Long officerId) {
        this.officerId = officerId;
    }
}
