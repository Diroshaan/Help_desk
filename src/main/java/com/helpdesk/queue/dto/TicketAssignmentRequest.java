package com.helpdesk.queue.dto;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Request body for routing or reassigning a ticket (PUT /api/queue/{id}/assign).
 *
 * Neither field is individually @NotNull - officerId alone is enough (the
 * department is inferred from that officer's own affiliation), and
 * departmentId alone routes the ticket to a department's queue without
 * picking a specific owner yet. Whether that combination is actually valid
 * (e.g. both missing, or an officerId that doesn't belong to the given
 * departmentId) is a business rule, not a shape rule, so it's enforced in
 * QueueService.assignTicket rather than here.
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
