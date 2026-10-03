package com.helpdesk.admin.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * The whole executive dashboard in one response body.
 *
 * Requirement specification 3.1: "Generate real-time executive dashboard
 * reports: total ticket volume, queue backlogs, average resolution times, SLA
 * breach metrics."
 *
 *
 * WHY ONE ENDPOINT AND ONE OBJECT RATHER THAN SIX SMALL ONES
 * ----------------------------------------------------------
 * A dashboard is read as a single picture of the system at one moment. Six
 * endpoints would mean six requests, each running at a slightly different time,
 * so the ticket total could be answered before a ticket is created and the
 * status breakdown after it - and the tiles on the screen would add up wrong,
 * intermittently, in a way nobody can reproduce. One request, one set of
 * numbers, one moment.
 *
 *
 * EVERY FIELD HERE IS DERIVED - NOTHING IS STORED
 * -----------------------------------------------
 * There is no dashboard_stats table behind this object and no ticketCount
 * column anywhere, because requirement 5 says metrics must be derived rather
 * than stored. Each number below is the result of a query run when this object
 * is built. See the class comment on TicketMetricsRepository for why a stored
 * count is a second copy of a fact that can disagree with the first.
 *
 * generatedAt is part of the payload for that reason: it is the timestamp these
 * numbers describe. A dashboard without one invites the reader to assume it is
 * current when the browser tab has been open since yesterday.
 */
public record DashboardResponse(

        /** Ticket volume by lifecycle state: {"OPEN": 12, "IN_PROGRESS": 5, ...}. */
        Map<String, Long> ticketsByStatus,

        /** Ticket volume by support desk, keyed by department code. */
        Map<String, Long> ticketsByDepartment,

        /**
         * Unfinished work by support desk - the queue backlog.
         *
         * Not the same number as ticketsByDepartment, and worth keeping
         * separate: a desk that has answered a thousand tickets and has two open
         * is not a desk in trouble, and a single "volume" figure cannot tell the
         * two situations apart.
         */
        Map<String, Long> openBacklogByDepartment,

        /** Every ticket ever raised, including withdrawn ones. */
        long totalTickets,

        /**
         * Mean hours from creation to resolution over resolved tickets, one
         * decimal. Null, not 0, when nothing has been resolved yet: zero would
         * read as an excellent real result rather than "no data".
         */
        Double averageResolutionHours,

        /**
         * Tickets past their service-level target and still unresolved.
         *
         * The specification never defines a breach, so F6 defines one. See
         * DashboardService.SLA_TARGET_HOURS and the comment on
         * TicketMetricsRepository.countSlaBreaches for the rule and why it was
         * chosen.
         */
        long slaBreaches,

        /** Accounts of every type, active and deactivated. */
        long totalUsers,

        /** Accounts that can currently log in. */
        long activeUsers,

        /** The moment these numbers were computed. */
        LocalDateTime generatedAt
) {
}
