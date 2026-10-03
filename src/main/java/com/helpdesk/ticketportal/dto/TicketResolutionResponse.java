package com.helpdesk.ticketportal.dto;

import java.time.LocalDateTime;

/**
 * The officer's answer to a ticket, as the student who owns the ticket sees it (F3, US WBHD-24).
 *
 * Deliberately has NO staff notes and no officer id. Staff notes are
 * officer-only (FR / US-16), so the student-facing shape simply has nowhere
 * to put them - internal notes cannot leak through this DTO even by mistake.
 *
 * publishedAt is Resolution.createdAt: Resolution has no field of that name,
 * "published" is what the moment means to the student.
 */
public record TicketResolutionResponse(
        Long id,
        String responseText,
        String officerName,
        String attachmentFileName,
        Long attachmentFileSize,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt) {
}
