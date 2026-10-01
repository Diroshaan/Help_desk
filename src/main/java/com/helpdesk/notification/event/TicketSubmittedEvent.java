package com.helpdesk.notification.event;

/**
 * OBSERVER PATTERN: a student has submitted a new ticket.
 *
 * Published by F2's TicketService.createTicket after the ticket is saved.
 * Listened to by QueueArrivalNotifier, which tells the officers of the
 * ticket's department (US-04: "alerted through my preferred channel when new
 * tickets land in my queue"). TicketService does not know who is listening,
 * exactly as QueueService does not know who hears about status changes.
 *
 * Plain values, not the Ticket entity, for the same reason as
 * TicketStatusChangedEvent: the listener runs after the transaction has
 * committed, when the entity is detached.
 *
 * assignedDepartmentCode is null today, because tickets are not yet routed at
 * creation. When F4-N5 (routing by category) sets it inside createTicket, the
 * publisher passes it and the listener uses it directly; until then the
 * listener finds the department through the ticket's category, which is the
 * same definition of "a department's tickets" the admin dashboard uses.
 */
public record TicketSubmittedEvent(Long ticketId,
                                   String subject,
                                   String categoryName,
                                   String assignedDepartmentCode) {
}
