package com.helpdesk.notification.event;

/**
 * Observer event: a student submitted a new ticket. Published by TicketService and
 * picked up by QueueArrivalNotifier, which alerts the department's officers.
 * assignedDepartmentCode may be null; the listener then uses the category's department.
 */
public record TicketSubmittedEvent(Long ticketId,
                                   String subject,
                                   String categoryName,
                                   String assignedDepartmentCode) {
}
