package com.helpdesk.admin.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The whole admin dashboard in one response, so every tile describes the same moment.
 * Nothing here is stored; each number is queried when the response is built, and
 * generatedAt says when that was.
 */
public record DashboardResponse(

        /** Ticket volume by lifecycle state: {"OPEN": 12, "IN_PROGRESS": 5, ...}. */
        Map<String, Long> ticketsByStatus,

        /** Ticket volume by support desk, keyed by department code. */
        Map<String, Long> ticketsByDepartment,

        /** Unfinished tickets per desk (the queue backlog), unlike the total volume above. */
        Map<String, Long> openBacklogByDepartment,

        /** Includes withdrawn tickets. */
        long totalTickets,

        /** Mean hours to resolve, one decimal. Null when nothing is resolved yet, since 0 would look like a great result. */
        Double averageResolutionHours,

        /** Unresolved tickets past the SLA target (see DashboardService.SLA_TARGET_HOURS). */
        long slaBreaches,

        /** All accounts, active and deactivated. */
        long totalUsers,

        /** Accounts that can currently log in. */
        long activeUsers,

        LocalDateTime generatedAt
) {
}
