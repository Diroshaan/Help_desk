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
 * Changing your own password - for EVERY account type.
 *
 * Until this existed there was no way to change a password anywhere in the
 * system. That was not a small gap:
 *   - the first-run administrator is created with a generated password and
 *     told to "sign in and change it", which was impossible;
 *   - every officer keeps, forever, the password the administrator typed when
 *     provisioning them - so the administrator can sign in as any officer, and
 *     nothing an officer does can be attributed to them with confidence;
 *   - a student who suspects their password has leaked had no remedy but to
 *     delete their account.
 *
 * WHY IT LIVES IN auth AND WORKS ON AppUser
 * -----------------------------------------
 * The password is a field of AppUser, the supertype, and "prove who you are"
 * is an authentication concern rather than any one feature's. Loading through
 * AppUserRepository, which is polymorphic across the JOINED hierarchy, means
 * one method serves students, officers and administrators alike, with no
 * instanceof and no three copies of the same logic.
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

    /**
     * Change the password of the account signed in as {@code email}.
     *
     * @param currentSessionId the session making the request - the one session
     *                         that is NOT ended afterwards
     */
    @Transactional
    public void changePassword(String email, String currentSessionId, PasswordChangeRequest request) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // passwordEncoder.matches(), never equals() on hashes. BCrypt salts
        // every hash, so encoding the same password twice gives two DIFFERENT
        // strings; the only correct comparison is matches(raw, storedHash),
        // which re-hashes the raw value with the salt stored inside the hash.
        //
        // IllegalArgumentException -> 400 through GlobalExceptionHandler, and
        // deliberately NOT 401. The frontend treats any 401 as "your session is
        // gone" and signs the user out (api.js, checkSessionLost). Mistyping
        // your current password is a form error, not a lost session - signing
        // someone out for a typo would be absurd.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Your current password is incorrect.");
        }

        // Refuse a "change" to the same password. It would satisfy every rule
        // and still leave the account exactly as exposed as before - the user
        // would believe they had secured it when nothing changed.
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Your new password must be different from your current one.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        appUserRepository.save(user);

        // Students have an activity log; officers and administrators do not
        // (ActivityLog belongs to Student), so only a student's change is
        // recorded there. The instanceof is honest here: the log genuinely only
        // exists for one subtype.
        if (user instanceof Student student) {
            activityLogService.record(student.getId(), ActivityType.PASSWORD_CHANGED,
                    "Password changed. If this wasn't you, contact the help desk immediately.");
        }

        // Last, after the new hash is saved: if the save had failed, the old
        // password would still be the valid one and ending sessions over it
        // would lock people out for nothing.
        sessionRevoker.revokeOtherSessions(user.getEmail(), currentSessionId);

        // Observer pattern: announce that the password changed and let the
        // notification module decide who to tell and how. This class doesn't
        // know notifications exist. The listener runs only after this
        // transaction commits, so a rollback above means no message goes out.
        eventPublisher.publishEvent(new PasswordChangedEvent(user.getId()));
    }
}
