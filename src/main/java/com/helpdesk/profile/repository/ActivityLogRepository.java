package com.helpdesk.profile.repository;

import com.helpdesk.profile.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /** Capped at 20 (what the profile page shows) so the log can't grow into a huge read. */
    List<ActivityLog> findTop20ByStudentIdOrderByOccurredAtDesc(Long studentId);
}
