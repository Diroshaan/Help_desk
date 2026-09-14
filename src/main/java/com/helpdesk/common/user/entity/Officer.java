package com.helpdesk.common.user.entity;

import com.helpdesk.common.reference.entity.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;

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
 * The specification asks for one more thing about officers, listed under
 * "Resolution & Queue Data", which is F4:
 *
 *   "The system must record that a senior officer supervises other officers,
 *    where each officer is supervised by at most one senior officer."
 *   -> a self-referencing @ManyToOne on this class
 *
 * It belongs on this entity when it is built, and it is F4's to build. It is
 * named here rather than left silent so the person who picks up that part of F4
 * can see exactly what is expected of them and where it goes, and so a reviewer
 * can tell the difference between "forgotten" and "not mine".
 *
 * The other queue-data item the specification asks for here - "which
 * departments each officer serves" - IS now built, below: a @ManyToMany to
 * Department.
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

    /**
     * The officer's own name, as students and colleagues see it.
     *
     * This was missing until now, and its absence was my omission when I built
     * this hierarchy: Officer was the only account type in the model with no way
     * to say who it was. F5 needs it to credit an article's author and F6 needs
     * it for the admin user listing, so both features had to work around a gap
     * in shared code rather than in their own.
     *
     * NOT NULL, unlike Administrator.staffNumber above: an officer with no name
     * is not a meaningful record. Every officer account is created deliberately
     * by an administrator through F6's provisioning endpoint, so there is never
     * a moment where the name is legitimately unknown - which is precisely the
     * difference from provisionedBy below, where "not recorded" is a real and
     * permanent state for some rows.
     *
     * @Size as well as @Column(length), for the reason spelled out on jobTitle.
     */
    @NotBlank(message = "An officer must have a full name")
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /**
     * The administrator who created this officer account (F6, WBHD-35).
     *
     * WHY NULLABLE, WHEN ALMOST EVERY OTHER FOREIGN KEY HERE IS NOT
     * -------------------------------------------------------------
     * Nullable is a true statement about the domain here, not a shortcut. Two
     * real cases have no provisioner and never will:
     *
     *   1. Accounts created before this column existed. Backfilling them with a
     *      guessed administrator would be inventing audit data, which is worse
     *      than recording that it is unknown.
     *   2. Nothing else - but see Administrator.provisionedBy, where the
     *      bootstrap account makes the same point more sharply.
     *
     * A NOT NULL column would force a lie in both cases. NULL reads as "not
     * recorded", which is what is actually true.
     *
     * LAZY rather than the @ManyToOne default of EAGER: the provisioner is audit
     * information, read on one admin screen and nowhere else. EAGER would make
     * every officer load - every login, every queue listing - fetch an
     * administrator row nobody asked for, and on a list of officers that is the
     * N+1 problem: one query for the list, then one more for every row in it.
     * The screen that wants this data can ask for it with a JOIN FETCH.
     *
     * The foreign key is named for the same reason Category's is: Hibernate
     * otherwise invents something like FKq7x2m1k, and a named constraint is what
     * makes the generated schema readable in the ER diagram the module asks for.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provisioned_by",
            foreignKey = @ForeignKey(name = "fk_officer_provisioned_by"))
    private Administrator provisionedBy;

    /**
     * The departments this officer serves - the many-to-many side of the
     * requirement quoted above the class comment. Owning side (the join table
     * lives here, not on Department), since "which departments does this
     * officer work" is the direction F4's queue scoping actually queries.
     */
    @ManyToMany
    @JoinTable(
        name = "officer_departments",
        joinColumns = @JoinColumn(name = "officer_id"),
        inverseJoinColumns = @JoinColumn(name = "department_code")
    )
    private Set<Department> departments = new HashSet<>();

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

    /**
     * Preferred constructor now that an officer has a name.
     *
     * The four-argument one above is kept rather than replaced so existing
     * callers (F6's provisioning service, the dev seeders) still compile - but
     * an Officer built that way has a null fullName and will be rejected by the
     * NOT NULL column at flush time. Callers should move to this one; the old
     * one should be deleted once none are left.
     */
    public Officer(String email, String password, String staffNumber, String jobTitle, String fullName) {
        this(email, password, staffNumber, jobTitle);
        this.fullName = fullName;
    }

    // --- Role ---

    @Override
    public Role getRole() {
        return Role.OFFICER;
    }

    /**
     * An officer's display name is their full name - see AppUser.getDisplayName().
     */
    @Override
    public String getDisplayName() {
        return fullName;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Administrator getProvisionedBy() {
        return provisionedBy;
    }

    public void setProvisionedBy(Administrator provisionedBy) {
        this.provisionedBy = provisionedBy;
    }

    public Set<Department> getDepartments() {
        return departments;
    }

    public void setDepartments(Set<Department> departments) {
        this.departments = departments;
    }
}
