package com.helpdesk.profile.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One line in a student's account history (F1 - "Dashboard & Activity View").
 *
 * Rows are written by ActivityLogService from inside the service methods that
 * perform the action, and read back by the profile page. Nothing ever updates
 * or deletes a row: an audit log that can be edited is not an audit log.
 *
 * WHY THERE IS NO @OneToMany ON Student
 * -------------------------------------
 * The obvious modelling choice would be a @ManyToOne back to Student, with a
 * matching @OneToMany collection on Student. This deliberately does neither,
 * for two reasons.
 *
 * The first is consistency: every other module in this project references a
 * student by plain id (Ticket.studentId, Bookmark.studentId, Feedback.studentId).
 * A single entity doing it differently would make the model harder to read, not
 * easier.
 *
 * The second is the one that actually matters. A @OneToMany on Student would be
 * lazy by default, and StudentResponse.from() runs in the controller - outside
 * the service's transaction. Touching a lazy collection there throws
 * LazyInitializationException; making it eager instead would drag the entire
 * history into every single profile fetch, forever, including the staff listing
 * that returns every account at once. Keeping the link as an id means the
 * service asks for exactly the rows it wants (the most recent 20), inside its
 * own transaction, and hands them to the response explicitly. Nothing is loaded
 * by accident and nothing depends on spring.jpa.open-in-view being left on.
 *
 * The trade-off, stated honestly: the database does not enforce that student_id
 * points at a real student. That check lives in the service instead. The EER for
 * IT2140 models this as a proper foreign key, and if the whole codebase moves to
 * JPA relationships this should move with it.
 */
@Entity
@Table(
        name = "activity_log",
        // The only query this table serves is "the newest entries for one
        // student". Without an index that becomes a full scan that gets slower
        // every time anybody logs in - the one table in the system guaranteed to
        // grow without limit. The column order matters: student_id first because
        // it is the equality filter, occurred_at second so the sort is satisfied
        // by the index rather than by a separate sort step.
        indexes = @Index(name = "idx_activity_student_time", columnList = "student_id, occurred_at")
)
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which student this entry belongs to. See the class comment for why this is an id. */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /**
     * @JdbcTypeCode(VARCHAR) is not decoration - it prevents a real trap.
     *
     * On MySQL, Hibernate 6 maps an @Enumerated(EnumType.STRING) field to the
     * database's NATIVE enum type, so the column becomes
     * enum('ACCOUNT_CREATED','LOGGED_IN',...) with the values baked into the
     * schema. That is what happened to Ticket.status, and it is a problem,
     * because ddl-auto=update never alters an existing column definition:
     * adding a constant in Java would compile, deploy, and then fail at insert
     * time with a data-truncation error that points nowhere near the cause.
     *
     * Forcing VARCHAR means a new ActivityType constant just works. The column
     * stores the constant's name either way, so nothing else changes.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private ActivityType type;

    /**
     * The sentence shown to the student, written when the event happened.
     *
     * Stored rather than derived from `type` at render time on purpose. An entry
     * should keep saying what it said when it was written - if the wording is
     * improved next sprint, history should not silently rewrite itself. It also
     * lets an entry carry detail the type alone cannot ("Profile updated: full
     * name, faculty").
     */
    @Column(nullable = false, length = 255)
    private String description;

    /** Set once, at construction. There is no setter - see the class comment. */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    public ActivityLog() {
        // Required no-argument constructor for JPA.
    }

    public ActivityLog(Long studentId, ActivityType type, String description) {
        this.studentId = studentId;
        this.type = type;
        this.description = description;
        this.occurredAt = LocalDateTime.now();
    }

    // --- Getters. No setters: a log entry is written once and never edited. ---

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
