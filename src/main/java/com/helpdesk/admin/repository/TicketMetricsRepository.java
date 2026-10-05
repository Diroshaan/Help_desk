package com.helpdesk.admin.repository;

import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only queries for the admin dashboard. Metrics are worked out from the ticket rows
 * each time instead of being stored, so they can't get out of sync (requirement 3.2).
 * Aggregates use GROUP BY so the database does the counting, not a Java loop.
 */
public interface TicketMetricsRepository extends Repository<Ticket, Long> {

    /** [status, count] per row; DashboardService turns it into a Map. */
    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countByStatus();

    /**
     * [department code, count] per row. Ticket.category holds a category name, and each
     * category belongs to one department, so we go through Category to find the desk.
     */
    @Query("SELECT c.department.code, COUNT(t) FROM Ticket t, Category c "
            + "WHERE c.name = t.category GROUP BY c.department.code")
    List<Object[]> countByDepartment();

    /** Queue backlog per desk: same as countByDepartment, but only the given statuses. */
    @Query("SELECT c.department.code, COUNT(t) FROM Ticket t, Category c "
            + "WHERE c.name = t.category AND t.status IN :statuses GROUP BY c.department.code")
    List<Object[]> countOpenByDepartment(@Param("statuses") List<TicketStatus> statuses);

    /**
     * [createdAt, resolvedAt] for resolved tickets. The average is done in Java because
     * timestamp subtraction differs between H2 and MySQL.
     */
    @Query("SELECT t.createdAt, t.resolvedAt FROM Ticket t "
            + "WHERE t.status = :resolved AND t.resolvedAt IS NOT NULL")
    List<Object[]> resolvedTimestamps(@Param("resolved") TicketStatus resolved);

    /**
     * Unresolved tickets older than the target for their priority (URGENT 8h, HIGH 24h,
     * MEDIUM 72h, LOW 120h). The spec doesn't define a breach, so this is our rule.
     * Resolved and withdrawn tickets don't count. The cutoffs are worked out in
     * DashboardService so the targets live in one place.
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :openStatuses AND ("
            + "(t.priority = com.helpdesk.ticket.entity.TicketPriority.URGENT AND t.createdAt < :urgentCutoff) OR "
            + "(t.priority = com.helpdesk.ticket.entity.TicketPriority.HIGH   AND t.createdAt < :highCutoff) OR "
            + "(t.priority = com.helpdesk.ticket.entity.TicketPriority.MEDIUM AND t.createdAt < :mediumCutoff) OR "
            + "(t.priority = com.helpdesk.ticket.entity.TicketPriority.LOW    AND t.createdAt < :lowCutoff))")
    long countSlaBreaches(@Param("openStatuses") List<TicketStatus> openStatuses,
                          @Param("urgentCutoff") LocalDateTime urgentCutoff,
                          @Param("highCutoff") LocalDateTime highCutoff,
                          @Param("mediumCutoff") LocalDateTime mediumCutoff,
                          @Param("lowCutoff") LocalDateTime lowCutoff);

    @Query("SELECT COUNT(t) FROM Ticket t")
    long countAll();
}
