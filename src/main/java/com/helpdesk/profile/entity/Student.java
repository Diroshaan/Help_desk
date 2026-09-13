package com.helpdesk.profile.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.helpdesk.common.user.entity.AppUser;
import com.helpdesk.common.user.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * F1 - Student Profile & Preferences Management (Diroshaan S, IT25101580)
 *
 * A university student. One of the three account types in the system.
 *
 *
 * WHAT CHANGED, AND WHY IT MATTERS
 * --------------------------------
 * This class used to be a standalone @Entity holding everything about an
 * account: id, email, password, role, active, createdAt, plus the
 * student-specific fields below. It now extends AppUser, and the first six of
 * those have moved up to the shared "users" table.
 *
 * The reason is the requirement specification, 3.2:
 *
 *   "The system must store a single user record for every system actor ...
 *    The system must record each user as exactly one of Student, Help Desk
 *    Officer or System Administrator, with role-specific data held only against
 *    the relevant type."
 *
 * What was here before could not satisfy that. There was one table, "students",
 * holding a free-text role column - so an officer was a student row with the
 * word OFFICER in it, an administrator was the same, and there was nowhere at
 * all to put an officer's job title or staff number. Three features were
 * blocked on that: F4 assigns tickets to officers, F5 records an article's
 * authoring officer, F6 provisions officer and admin accounts.
 *
 * Practical consequences of the move, worth knowing before reading further:
 *
 *   - getEmail(), getPassword(), isActive(), getCreatedAt() and their setters
 *     are all still available. They are inherited from AppUser, not declared
 *     here, so every existing caller keeps working unchanged.
 *   - getRole() now returns the Role ENUM rather than a String, and it is
 *     computed from the class rather than read from a column. Nothing can set
 *     it, which is the point - see below.
 *   - The JSON the API produces is unchanged. StudentResponse decides that, and
 *     it lists the same fields as before.
 *
 *
 * WHY setRole() NO LONGER EXISTS
 * ------------------------------
 * StudentService.register() used to call student.setRole("STUDENT") explicitly,
 * to defend against a mass-assignment attack: role was bound straight from the
 * request body, so POST /api/students {"role": "ADMIN"} on a public,
 * unauthenticated endpoint would have granted the caller administrator
 * privileges.
 *
 * That defence is now unnecessary, because the attack is no longer expressible.
 * Role is not a field, not a column, and has no setter; it is decided by which
 * Java class was instantiated, and this endpoint only ever instantiates a
 * Student. There is nothing for a request body to overwrite.
 *
 * That is the better kind of fix. The old line worked, but it worked by
 * remembering to write it - and a future refactor that dropped it would
 * reintroduce a privilege-escalation hole silently. Making the bad state
 * impossible to represent needs nobody to remember anything.
 */
@Entity
@Table(name = "students")
@PrimaryKeyJoinColumn(name = "id")
public class Student extends AppUser {

    /**
     * The university-issued registration number - the "additional unique
     * identifier" the specification asks for, alongside the system-generated id
     * inherited from AppUser.
     *
     * @NotBlank alone would accept any non-empty string, so "x" or "not an id"
     * would register a real account. @Pattern pins it to the university's actual
     * format: two letters then eight digits, e.g. IT25101580.
     *
     * Safe to enforce on the entity rather than only on the request DTO, unlike
     * the password rule: studentId is never transformed after input, and
     * ProfileUpdateRequest does not expose it for editing, so a value that
     * passes this check at registration keeps passing it on every later save.
     * The password could not be treated this way - Hibernate re-validates every
     * field on every save, and the stored BCrypt hash would fail a complexity
     * rule written for the plain text.
     */
    @NotBlank(message = "Student ID is required")
    @Pattern(regexp = "^[A-Z]{2}\\d{8}$",
             message = "Student ID must be two letters followed by eight digits, e.g. IT25101580")
    @Column(name = "student_id", unique = true, nullable = false, length = 20)
    private String studentId;

    /**
     * The student's name.
     *
     * KNOWN GAP: the specification asks for this as two columns - "the student's
     * name as separate given name and surname components" - and this is still
     * one. Splitting it changes the registration form, the profile page, both
     * request DTOs and StudentResponse, so it is deliberately a separate piece
     * of work rather than smuggled into the hierarchy change. Recorded here so
     * it is visibly outstanding rather than quietly missed.
     */
    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /**
     * The student's FACULTY - "Faculty of Computing", "Faculty of Engineering".
     *
     * NOT the same thing as common.reference.Department, which is a support desk
     * a ticket is routed to. The two concepts unfortunately share the English
     * word "department"; this field should be renamed to 'faculty' to remove the
     * ambiguity, which is a rename across the registration form, the profile
     * page and both DTOs and so is left as its own piece of work.
     *
     * No @NotBlank, deliberately: the faculty dropdown's first option is "Not
     * set" with value "", so a student clearing their faculty is a legitimate
     * save rather than an invalid one. This constraint has to be absent from the
     * ENTITY for that to work at all - Hibernate re-validates every field on
     * every save, so a @NotBlank here would reject the clearing save even with
     * the DTO-level check removed.
     */
    @Size(max = 100, message = "Faculty must be 100 characters or fewer")
    @Column(name = "department", length = 100)
    private String department;

    /**
     * The student's contact number.
     *
     * The frontend calls this "phone" in every payload it sends and reads
     * (Register.jsx, Profile.jsx), never "contactNumber". @JsonProperty fixes
     * both directions at once: without it, an incoming "phone" key silently
     * fails to bind - Jackson ignores unrecognised properties rather than
     * erroring - and an outgoing response serialises as "contactNumber", which
     * the frontend's student.phone read never finds. That was the bug that left
     * the phone box empty on the profile page even when a number was stored.
     *
     * KNOWN GAP: the specification says "the system must permit a student to
     * record more than one contact number" - a multivalued attribute, which in a
     * normalised design is its own table keyed by student id, not a column.
     * Like the name split, this is outstanding and deliberately separate.
     */
    @JsonProperty("phone")
    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    @Column(name = "contact_number", length = 30)
    private String contactNumber;

    @Size(max = 500, message = "Profile picture URL must be 500 characters or fewer")
    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    /**
     * Notification channel preferences (F1: toggle Email / Portal alerts).
     *
     * On Student rather than AppUser on purpose. These are preferences about
     * being told what happened to YOUR OWN tickets, which is a student concern;
     * an officer's notification needs are about queue assignment and are F4's to
     * define. Pushing them up to the supertype now would be guessing at a
     * requirement nobody has written, and the specification lists notification
     * preferences only under the student profile story.
     */
    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Column(name = "portal_notifications_enabled", nullable = false)
    private boolean portalNotificationsEnabled = true;

    // --- Constructors ---

    public Student() {
        // Required no-argument constructor for JPA
    }

    // --- Role ---

    /**
     * Always STUDENT, because this class is the student type.
     *
     * Read by StudentUserDetailsService to build the Spring Security authority
     * "ROLE_STUDENT", and by StudentResponse to report the role to the frontend.
     * There is no corresponding setter and no column behind it - see the class
     * comment above for why that is the whole point.
     */
    @Override
    public Role getRole() {
        return Role.STUDENT;
    }

    // --- Getters and setters ---
    //
    // Note there are none for id, email, password, active or createdAt: those
    // are inherited from AppUser. Redeclaring them here would shadow the
    // parent's and leave two fields where the code expects one.

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public boolean isPortalNotificationsEnabled() {
        return portalNotificationsEnabled;
    }

    public void setPortalNotificationsEnabled(boolean portalNotificationsEnabled) {
        this.portalNotificationsEnabled = portalNotificationsEnabled;
    }
}
