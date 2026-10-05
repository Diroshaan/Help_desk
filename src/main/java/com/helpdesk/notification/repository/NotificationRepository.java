package com.helpdesk.notification.repository;

import com.helpdesk.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** The inbox only shows the latest 50. */
    List<Notification> findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(Long recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    /** Matches on owner too, so someone else's notification is just "not found". */
    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    /** Single bulk UPDATE instead of loading each unread row. */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now "
            + "WHERE n.recipientUserId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
