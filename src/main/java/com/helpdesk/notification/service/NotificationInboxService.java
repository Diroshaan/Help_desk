package com.helpdesk.notification.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.notification.dto.NotificationResponse;
import com.helpdesk.notification.entity.Notification;
import com.helpdesk.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reading side of the portal channel: listing and marking notifications.
 *
 * Every method takes the caller's email from the session, never a user id
 * from the request, so nobody can read or mark someone else's inbox. A
 * notification that belongs to another user comes back as 404, not 403, the
 * same rule the rest of the project uses so ids can't be probed.
 */
@Service
public class NotificationInboxService {

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;

    public NotificationInboxService(NotificationRepository notificationRepository,
                                    AppUserRepository appUserRepository) {
        this.notificationRepository = notificationRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(String email) {
        return notificationRepository
                .findTop50ByRecipientUserIdOrderByCreatedAtDescIdDesc(userIdOf(email))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String email) {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(userIdOf(email));
    }

    @Transactional
    public void markRead(String email, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientUserId(notificationId, userIdOf(email))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.markRead();
    }

    @Transactional
    public int markAllRead(String email) {
        return notificationRepository.markAllRead(userIdOf(email), LocalDateTime.now());
    }

    private Long userIdOf(String email) {
        return appUserRepository.findByEmail(email)
                .map(AppUser::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
