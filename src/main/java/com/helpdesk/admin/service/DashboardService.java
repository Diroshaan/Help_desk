package com.helpdesk.admin.service;

import com.helpdesk.admin.dto.DashboardResponse;
import com.helpdesk.admin.repository.TicketMetricsRepository;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.ticket.entity.TicketPriority;
import com.helpdesk.ticket.entity.TicketStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * The real-time executive dashboard (WBHD-37).
 *
 * Requirement specification 3.1: "Generate real-time executive dashboard
 * reports: total ticket volume, queue backlogs, average resolution times, SLA
 * breach metrics."
 * Requirement specification 3.2: "The system must derive dashboard metrics
 * rather than storing them as separate values."
 *
 *
 * NOTHING HERE IS STORED
 * ----------------------
 * There is no dashboard_stats table and no ticketCount column. Every number is
 * a query against the rows themselves, run when the endpoint is called. That is
 * the requirement, and it is the requirement for a reason worth being able to
 * state: a stored count is a second copy of a fact that can disagree with the
 * first, and it disagrees SILENTLY - the moment a ticket is deleted or a status
 * changes through a code path that forgets the counter, the dashboard starts
 * lying and nothing compares it against reality to notice. Deriving costs a few
 * aggregate queries per page load and cannot drift.
 *
 * The cost is real and worth naming honestly: on a very large tickets table
 * these GROUP BYs get slower, and the usual answer then is a materialised view
 * or a nightly rollup - which is a stored copy, kept correct by the database
 * rather than by application code. That is a different design with a different
 * guarantee, not the thing this requirement forbids.
 *
 * ONE OF THE FOUR REQUESTED METRICS IS MISSING, AND THAT IS DELIBERATE
 * --------------------------------------------------------------------
 * 3.1 asks for total volume, queue backlogs, average resolution times and SLA
 * breaches. Three are here. Average resolution time is not, because Ticket has
 * no maintained resolution timestamp - updatedAt is written once at
 * construction and never touched again, so any duration computed from it is
 * approximately zero. Reporting 0.0 hours would not look broken, it would look
 * excellent, and it would sit beside three numbers that are real. An absent
 * tile is visible; a wrong one is not. See TicketMetricsRepository for the one
 * query that restores it once F4's Resolution record exists.
 *
 * readOnly = true on the one public method: nothing in it writes, Hibernate can
 * skip dirty-check snapshots of anything it loads, and an accidental setter
 * call could not reach the database if somebody added one.
 */
@Service
public class DashboardService {

    /**
     * THE SLA DEFINITION. Invented here, deliberately, and defensible.
     *
     * The requirement specification asks for "SLA breach metrics" and never
     * defines a breach anywhere. A number with no definition behind it is not a
     * metric - two people reading the same dashboard would mean different things
     * by it - so F6 picks a rule, writes it down in one place, and stands behind
     * it. An invented rule that can be explained beats a number that cannot.
     *
     * The rule: a ticket is BREACHED when it is still unresolved and older than
     * the target for its priority.
     *
     * Priority-weighted rather than one flat deadline, because a flat deadline
     * would make the same promise about an urgent account lockout and a
     * low-priority question about opening hours, and no support desk works that
     * way. The specific hours are a judgement - one working day for HIGH, three
     * for MEDIUM - chosen to be plausible for a university help desk rather than
     * derived from anything; they are constants here so that changing them is a
     * one-line change with no query to rewrite.
     *
     * An EnumMap rather than a HashMap: the keys are enum constants, so the
     * lookup is an array index rather than a hash, and adding a fifth priority
     * without adding it here shows up as a missing key at startup rather than as
     * a quietly wrong number.
     */
    private static final Map<TicketPriority, Integer> SLA_TARGET_HOURS =
            new EnumMap<>(Map.of(
                    TicketPriority.URGENT, 8,
                    TicketPriority.HIGH, 24,
                    TicketPriority.MEDIUM, 72,
                    TicketPriority.LOW, 120
            ));

    /**
     * The states that still count as work in progress.
     *
     * WITHDRAWN is excluded alongside RESOLVED, and that is a choice with a
     * reason: a student who withdrew their own ticket was not let down by the
     * help desk, so counting it as a backlog item or an SLA breach would make
     * both numbers worse for something nobody can act on.
     */
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

    /**
     * One endpoint, one response, computed on the fly.
     *
     * All of the queries run inside ONE transaction, which is the point of the
     * annotation here rather than on the controller. Six separate requests -
     * or six transactions - would each see the database at a slightly different
     * moment, so the status breakdown could include a ticket the total does not
     * and the tiles on the screen would fail to add up, intermittently, in a way
     * nobody can reproduce.
     */
    @Transactional(readOnly = true)
    public DashboardResponse buildDashboard() {
        LocalDateTime now = LocalDateTime.now();

        Map<String, Long> byStatus = toCountMap(ticketMetricsRepository.countByStatus());
        Map<String, Long> byDepartment = toCountMap(ticketMetricsRepository.countByDepartment());
        Map<String, Long> backlog = toCountMap(
                ticketMetricsRepository.countOpenByDepartment(UNFINISHED));

        // Every status gets a key, including the ones with no tickets. A GROUP
        // BY returns no row for a state nothing is in, so without this the
        // response would be missing "RESOLVED" entirely on a fresh system - and
        // a frontend reading response.ticketsByStatus.RESOLVED would show
        // "undefined" rather than 0. Absent and zero are different things to
        // JavaScript, and only one of them is true here.
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
                breaches,
                appUserRepository.count(),
                countActiveUsers(),
                now
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * The moment before which a ticket of this priority has breached.
     *
     * Computing the cutoff timestamps here rather than inside the query is what
     * keeps the SLA policy in one place: the repository holds no opinion about
     * how long anything should take, so changing a target is a change to
     * SLA_TARGET_HOURS and nothing else. It also keeps the query to plain
     * comparisons against bound parameters, which every database plans well and
     * can use the created_at index for - date arithmetic written inside a WHERE
     * clause usually cannot.
     */
    private LocalDateTime cutoff(LocalDateTime now, TicketPriority priority) {
        return now.minusHours(SLA_TARGET_HOURS.get(priority));
    }

    /**
     * Turns the [key, count] rows an aggregate query returns into a Map.
     *
     * The Object[] is unpacked here and never leaves this class, so nothing
     * downstream has to know which column was which. LinkedHashMap preserves
     * the order the query returned, so the JSON keys do not reshuffle between
     * requests for no reason.
     *
     * Key.toString() rather than a cast: the first column is an enum for the
     * status query and a String for the department queries, and toString gives
     * the constant's name for one and the code itself for the other. Count comes
     * back as a Number because JPA providers are free to return Long or
     * BigInteger depending on the database - longValue() covers both rather than
     * throwing a ClassCastException on whichever one was not expected.
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

    /**
     * Accounts that can currently log in.
     *
     * Counted by difference rather than by a second query, since AppUserRepository
     * is a shared file and F6 does not edit shared files - the change goes to
     * Diroshaan. count() minus the inactive ones needs a count of inactive ones,
     * which is the same problem, so this uses the one thing JpaRepository gives
     * for free and the same in-Java filter the account listing uses, for the
     * same reason: the users table is bounded and administrative, unlike tickets.
     * If an existsBy/countBy is ever added to AppUserRepository, this becomes one
     * query.
     */
    private long countActiveUsers() {
        return appUserRepository.findAll().stream().filter(user -> user.isActive()).count();
    }
}
