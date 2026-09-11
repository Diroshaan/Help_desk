package com.helpdesk.profile.service;

import com.helpdesk.profile.entity.ActivityLog;
import com.helpdesk.profile.entity.ActivityType;
import com.helpdesk.profile.repository.ActivityLogRepository;
import com.helpdesk.profile.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes and reads the account activity log (F1 - "Dashboard & Activity View").
 *
 * WHERE ENTRIES ARE WRITTEN FROM, AND WHY
 * ---------------------------------------
 * Every call to this class comes from a SERVICE method that is performing the
 * action being described - StudentService.register(), .updateProfile(),
 * .deactivate(), and AuthController for login. Three alternatives were
 * considered and rejected:
 *
 *   - from the CONTROLLER: the controller knows an HTTP request succeeded, not
 *     that the business operation committed. It would log "profile updated"
 *     for an update that later rolled back.
 *   - from an @Around ASPECT or an @EntityListener: fewer call sites, but the
 *     logging becomes invisible at the point it happens, and neither can easily
 *     say WHICH fields changed. It is also the kind of cleverness that is hard
 *     to defend when someone asks how it works.
 *   - from a Spring @EventListener: the right answer in a larger system, where
 *     the log is genuinely a separate concern. Here it adds an indirection for
 *     four call sites.
 *
 * Explicit calls from the service layer are slightly more code and much easier
 * to follow, and they land inside the caller's transaction - which is the point
 * of the next paragraph.
 *
 * TRANSACTIONS
 * ------------
 * record() uses the default propagation, so when it is called from inside
 * StudentService's @Transactional methods it JOINS that transaction rather than
 * starting its own. That is deliberate: if the profile update rolls back, the
 * line claiming the profile was updated must roll back with it. A log that can
 * disagree with the data is worse than no log.
 *
 * recordLoginQuietly() is the deliberate exception - see its own comment.
 */
@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final StudentRepository studentRepository;

    @Autowired
    public ActivityLogService(ActivityLogRepository activityLogRepository,
                              StudentRepository studentRepository) {
        this.activityLogRepository = activityLogRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * Records one event against a student.
     *
     * Joins the caller's transaction when there is one (see the class comment).
     */
    @Transactional
    public void record(Long studentId, ActivityType type, String description) {
        if (studentId == null) {
            return;                 // nothing sensible to attach the entry to
        }
        activityLogRepository.save(new ActivityLog(studentId, type, description));
    }

    /**
     * Records a successful login, and swallows any failure.
     *
     * This is the one place where a logging failure must NOT fail the operation,
     * and the reason is worth being precise about. By the time this is called,
     * authentication has already succeeded and the session already exists - the
     * student IS logged in. Letting a failed INSERT propagate would turn that
     * into a 500 and lock them out of an account they just proved they own,
     * because of a bookkeeping row. The event is worth recording; it is not
     * worth denying access over.
     *
     * Contrast this with record() above, which deliberately does NOT swallow
     * anything: there, the entry and the data it describes belong to the same
     * transaction and should live or die together. The difference is that a
     * profile update can be safely undone, and a login cannot be.
     *
     * Takes the email rather than an id because that is all Spring Security has
     * at this point - Authentication.getName() is the email the student logged
     * in with (see StudentUserDetailsService).
     */
    @Transactional
    public void recordLoginQuietly(String email) {
        try {
            studentRepository.findByEmail(email).ifPresent(student ->
                    activityLogRepository.save(new ActivityLog(
                            student.getId(), ActivityType.LOGGED_IN, "Signed in.")));
        } catch (RuntimeException ignored) {
            // See above. Intentionally swallowed.
        }
    }

    /**
     * The most recent entries for one student, newest first, for the profile page.
     */
    @Transactional(readOnly = true)
    public List<ActivityLog> recentFor(Long studentId) {
        if (studentId == null) {
            return List.of();
        }
        return activityLogRepository.findTop20ByStudentIdOrderByOccurredAtDesc(studentId);
    }
}
