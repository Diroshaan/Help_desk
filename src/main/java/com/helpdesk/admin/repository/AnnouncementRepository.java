package com.helpdesk.admin.repository;

import com.helpdesk.admin.entity.Announcement;
import com.helpdesk.common.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * F6 - System Analytics, Provisioning & Announcements
 *
 * Data access for system-wide notices.
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /**
     * Every announcement, expired ones included, newest first - the
     * administrator's management list.
     *
     * JOIN FETCH rather than the derived findAllByOrderByPublishedAtDesc(),
     * because the response carries the publisher's display name and
     * Announcement.publishedBy is LAZY. Without the fetch this is the N+1
     * problem: one query for the page of announcements, then one more per row
     * the moment each name is read. With it, one query. Same reasoning as
     * CategoryRepository.findSelectableWithDepartment - see the comment there.
     *
     * JOIN, not LEFT JOIN: publishedBy is optional = false, so an announcement
     * without an administrator cannot exist and an inner join can never silently
     * drop a row.
     */
    @Query("SELECT a FROM Announcement a JOIN FETCH a.publishedBy "
            + "ORDER BY a.publishedAt DESC")
    List<Announcement> findAllWithPublisher();

    /** One announcement with its publisher already loaded, for the edit and read paths. */
    @Query("SELECT a FROM Announcement a JOIN FETCH a.publishedBy WHERE a.id = :id")
    Optional<Announcement> findByIdWithPublisher(@Param("id") Long id);

    /**
     * The live notices one particular caller is allowed to see.
     *
     * Three conditions, and each is in the query rather than in Java for the
     * same reason DepartmentRepository filters on active: the database should
     * return the rows that are wanted, not every row for the application to sift
     * through. On a table that grows by one row per notice that hardly matters;
     * the habit is what matters, and the identical mistake against the tickets
     * table is a real problem.
     *
     *   a.publishedAt <= :now      - not scheduled for the future
     *   expiry                     - NULL means never expires, so the null case
     *                                is spelled out rather than left to SQL's
     *                                three-valued logic, where
     *                                "NULL > now" is UNKNOWN and the row
     *                                silently disappears
     *   visibility                 - an EMPTY role set means visible to
     *                                everyone. See Announcement.isVisibleTo for
     *                                why that is the chosen reading.
     *
     * "IS EMPTY" on the element collection, not "size = 0": both work, but
     * IS EMPTY is the JPQL operator meant for it and Hibernate renders it as a
     * NOT EXISTS subquery rather than a correlated COUNT.
     */
    @Query("SELECT a FROM Announcement a "
            + "WHERE a.publishedAt <= :now "
            + "AND (a.expiresAt IS NULL OR a.expiresAt > :now) "
            + "AND (a.visibleToRoles IS EMPTY OR :role MEMBER OF a.visibleToRoles) "
            + "ORDER BY a.publishedAt DESC")
    List<Announcement> findLiveForRole(@Param("now") LocalDateTime now,
                                       @Param("role") Role role);
}
