package com.helpdesk.admin.repository;

import com.helpdesk.admin.entity.Announcement;
import com.helpdesk.common.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Repository for announcements. */
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    // All announcements, newest first, for the admin list. JOIN FETCH loads the
    // publisher in the same query (publishedBy is lazy), avoiding N+1 queries.
    @Query("SELECT a FROM Announcement a JOIN FETCH a.publishedBy "
            + "ORDER BY a.publishedAt DESC")
    List<Announcement> findAllWithPublisher();

    /** One announcement with its publisher already loaded. */
    @Query("SELECT a FROM Announcement a JOIN FETCH a.publishedBy WHERE a.id = :id")
    Optional<Announcement> findByIdWithPublisher(@Param("id") Long id);

    /**
     * Live notices this role may see: already published, not expired (null expiry
     * means never, checked explicitly because NULL > now is never true), and
     * visible to everyone (empty role set) or to this role.
     */
    @Query("SELECT a FROM Announcement a "
            + "WHERE a.publishedAt <= :now "
            + "AND (a.expiresAt IS NULL OR a.expiresAt > :now) "
            + "AND (a.visibleToRoles IS EMPTY OR :role MEMBER OF a.visibleToRoles) "
            + "ORDER BY a.publishedAt DESC")
    List<Announcement> findLiveForRole(@Param("now") LocalDateTime now,
                                       @Param("role") Role role);
}
