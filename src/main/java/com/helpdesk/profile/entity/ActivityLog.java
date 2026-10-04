package com.helpdesk.profile.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One line in a student's account history. Rows are only ever inserted, never edited.
 * studentId is a plain id like Ticket.studentId, so profile fetches never load the
 * whole history; the downside is no foreign key in the database.
 */
@Entity
@Table(
        name = "activity_log",
        // matches the one query: newest entries for one student
        indexes = @Index(name = "idx_activity_student_time", columnList = "student_id, occurred_at")
)
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /**
     * Stored as VARCHAR, not a MySQL native ENUM, because ddl-auto=update never
     * changes an ENUM column, so a new ActivityType value would fail on insert.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private ActivityType type;

    // saved as written, so old entries keep their wording and details ("full name, faculty")
    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    public ActivityLog() {
        // for JPA
    }

    public ActivityLog(Long studentId, ActivityType type, String description) {
        this.studentId = studentId;
        this.type = type;
        this.description = description;
        this.occurredAt = LocalDateTime.now();
    }

    // no setters: entries are never edited

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public ActivityType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
