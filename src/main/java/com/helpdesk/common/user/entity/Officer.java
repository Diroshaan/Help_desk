package com.helpdesk.common.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SHARED USER MODEL - not owned by any single feature.
 *
 * A Help Desk Officer: the staff member who works a departmental queue, answers
 * tickets and publishes knowledge base articles.
 *
 * Requirement specification 3.2, User & Profile Data:
 *   "The system must store each officer's job title and staff identification
 *    number."
 *
 * That single sentence is the whole of this class. Both columns can be NOT NULL
 * here precisely because this table holds only officers - which is the
 * guarantee the JOINED strategy buys and SINGLE_TABLE could not have given.
 *
 *
 * WHAT IS DELIBERATELY NOT HERE
 * -----------------------------
 * The specification asks for two more things about officers, and both are
 * listed under "Resolution & Queue Data", which is F4:
 *
 *   "The system must record that a senior officer supervises other officers,
 *    where each officer is supervised by at most one senior officer."
 *   -> a self-referencing @ManyToOne on this class
 *
 *   "The system must record which departments each officer serves, where an
 *    officer may serve one or more departments and a department may be served
 *    by many officers."
 *   -> a @ManyToMany to Department
 *
 * Both belong on this entity when they are built, and both are F4's to build.
 * They are named here rather than left silent so the person who picks up F4 can
 * see exactly what is expected of them and where it goes, and so a reviewer can
 * tell the difference between "forgotten" and "not mine".
 *
 *
 * WHY THIS CLASS EXISTS NOW, BEFORE ANYONE NEEDS AN OFFICER SCREEN
 * ---------------------------------------------------------------
 * Three features are blocked without it. F4 assigns a ticket to an officer. F5
 * records the authoring officer of every article. F6 provisions officer
 * accounts. None of them can define a foreign key to a table that does not
 * exist, so all three were waiting on a class nobody owned.
 *
 * Creating officer accounts is a privileged, access-controlled operation and
 * belongs to F6 - there is deliberately no service or controller here, only the
 * entity and its repository.
 */
@Entity
@Table(name = "officers")
@PrimaryKeyJoinColumn(name = "id")
public class Officer extends AppUser {

    /**
     * The staff identification number - the officer's equivalent of a student's
     * registration number, and unique for the same reason.
     *
     * Kept as a String rather than a number: a staff number is an identifier
     * that happens to contain digits, not a quantity. Nothing ever adds two of
     * them together, leading zeros must survive, and the university is free to
     * introduce a letter prefix without a schema change.
     *
     * No @Pattern, unlike Student.studentId. The student ID format is published
     * and stable (two letters, eight digits). No equivalent format for staff
     * numbers appears anywhere in the requirement specification, and inventing
     * one here would reject valid data the moment a real number did not match a
     * guess. @Size bounds it; the format is left open until it is actually
     * known.
     */
    @NotBlank(message = "Staff number is required")
    @Size(max = 20, message = "Staff number must be 20 characters or fewer")
    @Column(name = "staff_number", nullable = false, unique = true, length = 20)
    private String staffNumber;

    /**
     * The officer's job title, e.g. "Senior Support Officer".
     *
     * @Size(max = 100) matches the column length deliberately. A @Column length
     * with no matching @Size is a trap this codebase has already fallen into
     * three times (Ticket.description, Feedback.comment, BookmarkFolder.name):
     * over-length input passes bean validation, fails at the database, and
     * GlobalExceptionHandler reports the resulting DataIntegrityViolationException
     * as "That value is already in use by another account" - wrong status, wrong
     * message, and no clue where to look.
     */
    @NotBlank(message = "Job title is required")
    @Size(max = 100, message = "Job title must be 100 characters or fewer")
    @Column(name = "job_title", nullable = false, length = 100)
    private String jobTitle;

    // --- Constructors ---

    public Officer() {
        // Required no-argument constructor for JPA
    }

    public Officer(String email, String password, String staffNumber, String jobTitle) {
        setEmail(email);
        setPassword(password);
        this.staffNumber = staffNumber;
        this.jobTitle = jobTitle;
    }

    // --- Role ---

    @Override
    public Role getRole() {
        return Role.OFFICER;
    }

    // --- Getters and setters ---

    public String getStaffNumber() {
        return staffNumber;
    }

    public void setStaffNumber(String staffNumber) {
        this.staffNumber = staffNumber;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
}
