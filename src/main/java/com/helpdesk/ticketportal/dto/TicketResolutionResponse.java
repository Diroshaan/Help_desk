package com.helpdesk.ticketportal.dto;

import java.time.LocalDateTime;

/**
 * The officer's answer to a ticket, as the owning student sees it.
 * Has no staff notes or officer id, so internal notes can't leak to students.
 * publishedAt is the resolution's createdAt.
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
