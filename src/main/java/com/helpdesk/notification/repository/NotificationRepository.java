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

    /** The inbox shows the latest 50. Older ones stay in the table but nobody scrolls that far. */
    List<Notification> findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(Long recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    /** Looks up by id AND owner together, so someone else's notification is simply "not found". */
    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    /** One UPDATE instead of loading every unread row just to set a timestamp on each. */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now "
            + "WHERE n.recipientUserId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
