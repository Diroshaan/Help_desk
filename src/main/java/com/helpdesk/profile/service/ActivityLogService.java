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
 * Writes and reads a student's account activity log.
 * Called from the service methods doing the action, so each entry joins the same
 * transaction and rolls back with it if the action fails.
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

    @Transactional
    public void record(Long studentId, ActivityType type, String description) {
        if (studentId == null) {
            return;
        }
        activityLogRepository.save(new ActivityLog(studentId, type, description));
    }

    /**
     * Records a login and ignores any failure. The student is already signed in by
     * now, so a failed log insert shouldn't turn into a 500 for them.
     */
    @Transactional
    public void recordLoginQuietly(String email) {
        try {
            studentRepository.findByEmail(email).ifPresent(student ->
                    activityLogRepository.save(new ActivityLog(
                            student.getId(), ActivityType.LOGGED_IN, "Signed in.")));
        } catch (RuntimeException ignored) {
            // logging must not block the login
        }
    }

    /** Newest 20 entries, for the profile page. */
    @Transactional(readOnly = true)
    public List<ActivityLog> recentFor(Long studentId) {
        if (studentId == null) {
            return List.of();
        }
        return activityLogRepository.findTop20ByStudentIdOrderByOccurredAtDesc(studentId);
    }
}
