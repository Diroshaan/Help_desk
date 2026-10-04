package com.helpdesk.admin.service;

import com.helpdesk.admin.dto.DashboardResponse;
import com.helpdesk.admin.repository.TicketMetricsRepository;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the admin dashboard. Every number is queried from the ticket and user rows when
 * asked for; nothing is stored, so the figures can't drift out of date (requirement 3.2).
 */
@Service
public class DashboardService {

    /**
     * Our SLA rule (the spec doesn't define one): a ticket is breached when it is still
     * unresolved and older than the target hours for its priority. Change the numbers
     * here and the query picks them up.
     */
    private static final Map<TicketPriority, Integer> SLA_TARGET_HOURS =
            new EnumMap<>(Map.of(
                    TicketPriority.URGENT, 8,
                    TicketPriority.HIGH, 24,
                    TicketPriority.MEDIUM, 72,
                    TicketPriority.LOW, 120
            ));

    // Withdrawn tickets aren't the desk's fault, so they don't count as backlog or breaches.
    private static final List<TicketStatus> UNFINISHED =
            List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

    private final TicketMetricsRepository ticketMetricsRepository;
    private final AppUserRepository appUserRepository;

    @Autowired
    public DashboardService(TicketMetricsRepository ticketMetricsRepository,
                            AppUserRepository appUserRepository) {
        this.ticketMetricsRepository = ticketMetricsRepository;
        this.appUserRepository = appUserRepository;
    }

    // One read-only transaction, so all the tiles describe the same moment.
    @Transactional(readOnly = true)
    public DashboardResponse buildDashboard() {
        LocalDateTime now = LocalDateTime.now();

        Map<String, Long> byStatus = toCountMap(ticketMetricsRepository.countByStatus());
        Map<String, Long> byDepartment = toCountMap(ticketMetricsRepository.countByDepartment());
        Map<String, Long> backlog = toCountMap(
                ticketMetricsRepository.countOpenByDepartment(UNFINISHED));

        // GROUP BY skips statuses with no tickets; add them as 0 so the frontend
        // doesn't get undefined.
        for (TicketStatus status : TicketStatus.values()) {
            byStatus.putIfAbsent(status.name(), 0L);
        }

        long breaches = ticketMetricsRepository.countSlaBreaches(
                UNFINISHED,
                cutoff(now, TicketPriority.URGENT),
                cutoff(now, TicketPriority.HIGH),
                cutoff(now, TicketPriority.MEDIUM),
                cutoff(now, TicketPriority.LOW));

        return new DashboardResponse(
                byStatus,
                byDepartment,
                backlog,
                ticketMetricsRepository.countAll(),
                averageResolutionHours(),
                breaches,
                appUserRepository.count(),
                countActiveUsers(),
                now
        );
    }

    // Null when nothing is resolved yet. Minutes / 60.0 keeps the part-hours that toHours() would drop.
    private Double averageResolutionHours() {
        List<Object[]> rows = ticketMetricsRepository.resolvedTimestamps(TicketStatus.RESOLVED);
        if (rows.isEmpty()) {
            return null;
        }
        double totalMinutes = 0;
        for (Object[] row : rows) {
            totalMinutes += Duration.between((LocalDateTime) row[0], (LocalDateTime) row[1]).toMinutes();
        }
        double hours = totalMinutes / rows.size() / 60.0;
        return Math.round(hours * 10.0) / 10.0;
    }

    // Tickets created before this time have breached for the given priority.
    private LocalDateTime cutoff(LocalDateTime now, TicketPriority priority) {
        return now.minusHours(SLA_TARGET_HOURS.get(priority));
    }

    /**
     * Turns [key, count] rows into a Map, keeping the query's order. The count is read
     * as a Number because the provider may return Long or BigInteger.
     */
    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            counts.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    // Filtered in Java because AppUserRepository has no countByActive; the user table is small.
    private long countActiveUsers() {
        return appUserRepository.findAll().stream().filter(user -> user.isActive()).count();
    }
}
