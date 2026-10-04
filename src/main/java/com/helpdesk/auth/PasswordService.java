package com.helpdesk.auth;

import com.helpdesk.auth.dto.PasswordChangeRequest;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.common.user.repository.AppUserRepository;
import com.helpdesk.profile.entity.ActivityType;
import com.helpdesk.profile.service.ActivityLogService;
import com.helpdesk.notification.event.PasswordChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets any signed-in user (student, officer or admin) change their own password. Works on
 * AppUser so one method covers all three account types.
 */
@Service
public class PasswordService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevoker sessionRevoker;
    private final ActivityLogService activityLogService;
    private final ApplicationEventPublisher eventPublisher;

    public PasswordService(AppUserRepository appUserRepository,
                           PasswordEncoder passwordEncoder,
                           SessionRevoker sessionRevoker,
                           ActivityLogService activityLogService,
                           ApplicationEventPublisher eventPublisher) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevoker = sessionRevoker;
        this.activityLogService = activityLogService;
        this.eventPublisher = eventPublisher;
    }

    // currentSessionId is the session that stays signed in; all others are ended.
    @Transactional
    public void changePassword(String email, String currentSessionId, PasswordChangeRequest request) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // BCrypt salts each hash, so compare with matches(), not equals().
        // A wrong current password is a 400, not 401 - the frontend logs the user out on any 401.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Your current password is incorrect.");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Your new password must be different from your current one.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        appUserRepository.save(user);

        // Only students have an activity log.
        if (user instanceof Student student) {
            activityLogService.record(student.getId(), ActivityType.PASSWORD_CHANGED,
                    "Password changed. If this wasn't you, contact the help desk immediately.");
        }

        // Ends other sessions in case someone else knows the old password.
        sessionRevoker.revokeOtherSessions(user.getEmail(), currentSessionId);

        // Observer: publish PasswordChangedEvent and let the notification module tell the user.
        // The listener runs after commit, so a rollback sends nothing.
        eventPublisher.publishEvent(new PasswordChangedEvent(user.getId()));
    }
}
