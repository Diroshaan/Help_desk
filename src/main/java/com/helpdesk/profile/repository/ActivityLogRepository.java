package com.helpdesk.profile.repository;

import com.helpdesk.profile.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Data access for the account activity log (F1 - "Dashboard & Activity View").
 */
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * The most recent entries for one student, newest first.
     *
     * "Top20" is in the method name, not a parameter, deliberately: this is the
     * only read the profile page performs, and an unbounded findByStudentId
     * would work perfectly for weeks and then start returning thousands of rows
     * on the one account that is used every day - the classic version of this
     * bug, where the query is fine in testing and slow only in the demo.
     * Bounding it here means no caller can forget to.
     *
     * Spring Data derives the query from the name: findTop20 = LIMIT 20,
     * ByStudentId = the WHERE clause, OrderByOccurredAtDesc = newest first.
     * No SQL to write, and it is checked against the entity at startup - a typo
     * in a field name fails the application context rather than at first use.
     *
     * When a full paged history is needed, this becomes a Pageable query and
     * gets its own endpoint. Twenty is what the profile page shows.
     */
    List<ActivityLog> findTop20ByStudentIdOrderByOccurredAtDesc(Long studentId);
}
