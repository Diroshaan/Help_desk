package com.helpdesk.admin.repository;

import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Every number on the executive dashboard, computed by the database.
 *
 *
 * WHY THERE IS NO dashboard_stats TABLE AND NO ticketCount COLUMN
 * ---------------------------------------------------------------
 * Requirement specification 3.2: "The system must derive dashboard metrics
 * rather than storing them as separate values."
 *
 * A stored count is a second copy of a fact that can disagree with the first.
 * The moment a ticket is deleted, or a status changes through a code path that
 * forgets to update the counter, or two requests increment it at once, the
 * dashboard starts lying - and it lies quietly, because nothing compares the
 * stored number against reality. Deriving means the answer is recomputed from
 * the rows themselves every time it is asked for, so it cannot drift. It is the
 * same reasoning as AppUser having no 'role' column: a value that can be
 * computed from facts already recorded should not be recorded again.
 *
 *
 * WHY THIS IS A SEPARATE REPOSITORY OVER Ticket
 * ---------------------------------------------
 * TicketRepository belongs to F2 and these queries are not F2's concern.
 * Spring Data is perfectly happy with two repository interfaces over the same
 * entity, and a second one keeps the reporting queries in the feature that
 * needs them - so F6 adds no lines to a file two other people are editing.
 * (PR #29 is blocked by exactly the kind of collision that avoids.)
 *
 * It extends the bare Repository marker, not JpaRepository, on purpose: this is
 * a read-only reporting view of the ticket table and should not expose save(),
 * delete() or findAll() to anything in the admin package.
 *
 *
 * WHY EVERY AGGREGATE IS A GROUP BY AND NOT A JAVA LOOP
 * ----------------------------------------------------
 * FeedbackService.summaryByCategory in this codebase loads every ticket and
 * every feedback row into memory to compute an average that one GROUP BY would
 * produce. That transfers the whole table over the network to throw almost all
 * of it away, and gets slower every day the system is used. The database
 * already has the rows and an engine built to aggregate them; asking it for the
 * answer instead of the raw data is the whole point of having one.
 */
public interface TicketMetricsRepository extends Repository<Ticket, Long> {

    /**
     * Ticket volume per lifecycle state: [status, count] per row.
     *
     * Object[] rather than a projection interface because the first element is
     * an enum, not a scalar the JPA provider can bind to a getter by name. The
     * service unpacks it immediately into a Map so the Object[] never escapes
     * the data layer - see DashboardService.
     */
    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countByStatus();

    /**
     * Ticket volume per support desk: [department code, count] per row.
     *
     * THE JOIN IS ON Department.name, AND THAT NEEDS EXPLAINING
     * --------------------------------------------------------
     * Ticket.category is still a free-text String - F2 has not converted it to a
     * reference to common.reference.Category - so there is no association to
     * navigate and the two tables have to be matched on text.
     *
     * The obvious reading of the column name says to match it against
     * Category.name. That is wrong today: the values F2 actually writes into
     * that column are DEPARTMENT names ("IT Services", "Registration"), not
     * category names ("Password & account access"), so a join to Category.name
     * matches nothing at all and this breakdown comes back empty on a database
     * full of tickets - the worst kind of wrong, because an empty chart looks
     * like a quiet system rather than a broken query. Matching Department.name
     * is what the data in the column supports.
     *
     * Written as a JPQL cross join with a WHERE clause rather than an ON clause,
     * because the plain comma form is standard JPQL and works on any provider,
     * whereas an ad-hoc "JOIN Department d ON ..." between unrelated entities is
     * a Hibernate extension.
     *
     * The honest limitation, and the reason this is temporary: a ticket whose
     * category text matches no department name contributes to nothing and is
     * silently absent here. Free text cannot be joined reliably. This whole
     * query becomes a real association the day F2 makes Ticket.category a
     * foreign key, and it should be revisited then - at which point the route to
     * a department goes through the category, as the specification intends.
     * DashboardService reports totalTickets separately so the two can be
     * compared and a gap noticed.
     */
    @Query("SELECT d.code, COUNT(t) FROM Ticket t, Department d "
            + "WHERE d.name = t.category GROUP BY d.code")
    List<Object[]> countByDepartment();

    /**
     * Unfinished work per support desk - the queue backlog the specification
     * asks for, which is not the same number as total volume: a desk that has
     * answered a thousand tickets and has two open is not a desk in trouble.
     *
     * Same Department.name join, same reason and same limitation as above.
     */
    @Query("SELECT d.code, COUNT(t) FROM Ticket t, Department d "
            + "WHERE d.name = t.category AND t.status IN :statuses GROUP BY d.code")
    List<Object[]> countOpenByDepartment(@Param("statuses") List<TicketStatus> statuses);

    /*
     * THERE IS DELIBERATELY NO averageResolutionMinutes() QUERY HERE.
     * --------------------------------------------------------------
     * The specification asks for average resolution time and this feature cannot
     * honestly compute it yet, so it reports nothing rather than something.
     *
     * The only two timestamps a ticket has are createdAt and updatedAt, and
     * updatedAt is not maintained: Ticket has no @PreUpdate and no code path
     * sets it, so it is written once at construction and keeps its initial
     * value forever. An average over timestampdiff(createdAt, updatedAt) would
     * therefore return approximately zero on every row - and zero minutes is not
     * an obviously broken number, it is a plausible-looking one. A metric that
     * is silently always 0.0 is worse than an absent metric, because the
     * dashboard reports it with the same confidence as the numbers that are
     * real, and nobody thinks to check it.
     *
     * The missing piece is a genuine resolution timestamp, which belongs to F4's
     * Resolution record. When that exists this is one query:
     *
     *     SELECT AVG(timestampdiff(MINUTE, t.createdAt, r.createdAt))
     *     FROM Resolution r JOIN r.ticket t
     *
     * and the field returns to DashboardResponse. Until then the tile is absent
     * from the API rather than present and lying.
     */

    /**
     * Tickets that have breached their service-level target.
     *
     * THE SLA RULE IS INVENTED, AND DELIBERATELY SO
     * ---------------------------------------------
     * The requirement specification asks for "SLA breach metrics" and never
     * defines a breach. A number with no definition behind it is not a metric,
     * so F6 picks one, writes it down as named constants in DashboardService,
     * and stands behind it:
     *
     *   a ticket is BREACHED when it is still unresolved and older than the
     *   target for its priority - URGENT 8h, HIGH 24h, MEDIUM 72h, LOW 120h.
     *
     * Priority-weighted, because a single flat deadline would treat an urgent
     * account lockout and a low-priority query about opening hours as the same
     * promise, and no support desk works that way.
     *
     * Counting only STILL-UNRESOLVED tickets is the second half of the choice.
     * A breach defined this way is actionable: every ticket in the number is one
     * somebody can still go and fix today, which is what an operational
     * dashboard is for. A historical count of "tickets that were eventually
     * answered late" is also a legitimate metric, and it is a different one -
     * it belongs beside this number, not inside it.
     *
     * WITHDRAWN is excluded along with RESOLVED: a student who withdrew their
     * own ticket was not let down by the help desk, and counting it as a breach
     * would make the number worse for a reason nobody can act on.
     *
     * The four cutoff timestamps are computed in the service and passed in, so
     * this query holds no policy of its own - change the targets in one place
     * and nothing here has to be touched.
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

    /** Total tickets ever raised, for the headline tile. */
    @Query("SELECT COUNT(t) FROM Ticket t")
    long countAll();
}
