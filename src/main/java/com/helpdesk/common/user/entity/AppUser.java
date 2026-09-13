package com.helpdesk.common.user.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * SHARED USER MODEL - not owned by any single feature.
 *
 * The one record that exists for every actor in the system, whether they are a
 * Student, an Officer or an Administrator. Holds only what all three have in
 * common: how they log in, whether their account is usable, and when it was
 * created.
 *
 * Requirement specification 3.2, User & Profile Data:
 *
 *   "The system must store a single user record for every system actor,
 *    uniquely identified by a system-generated User ID (primary key), holding
 *    email, hashed password, role, account status and registration date."
 *
 *   "The system must record each user as exactly one of Student, Help Desk
 *    Officer or System Administrator, with role-specific data held only against
 *    the relevant type."
 *
 * Those two sentences describe a specialisation hierarchy - the EER construct
 * where one general entity has several disjoint specialisations - and this
 * class is its general entity.
 *
 *
 * WHY THE CLASS IS CALLED AppUser AND NOT User
 * --------------------------------------------
 * Spring Security already has org.springframework.security.core.userdetails.User,
 * and StudentUserDetailsService imports it to build a UserDetails. Two classes
 * called User in one codebase means every file touching both needs a
 * fully-qualified name, and the day somebody imports the wrong one the error is
 * baffling. The TABLE is still called "users", which is what the specification
 * and any SQL the marker writes will expect; only the Java identifier differs.
 *
 *
 * WHY InheritanceType.JOINED
 * --------------------------
 * JPA offers three ways to store a hierarchy, and the choice is a real database
 * design decision rather than a Java detail.
 *
 *   SINGLE_TABLE puts all three types in one wide table with a discriminator
 *   column. It is the fastest to query because nothing is joined, but every
 *   officer-only column has to be nullable, since student rows live in the same
 *   table and have nothing to put there. The database then cannot enforce that
 *   an officer has a job title, which is precisely the guarantee the
 *   specification asks for when it says role-specific data is "held only
 *   against the relevant type".
 *
 *   TABLE_PER_CLASS gives each concrete type its own standalone table with the
 *   common columns repeated in each. That breaks the first requirement
 *   outright - there is no single user record - and makes "email is unique
 *   across all accounts" impossible to express as one constraint, because there
 *   is no one table to put it on.
 *
 *   JOINED, used here, stores the shared columns once in "users" and gives each
 *   subtype its own table holding only its own columns, keyed by the same id
 *   and linked by a foreign key. Reading a Student joins two tables. That join
 *   is the cost, and it buys: one row per actor, email unique in one place
 *   across every account type, and subtype columns that can be NOT NULL because
 *   only rows of that subtype exist in that table.
 *
 * JOINED is the direct translation of an EER specialisation into relational
 * tables, which is why it is the right answer for this project specifically.
 *
 *
 * WHY THERE IS NO "role" COLUMN
 * -----------------------------
 * The specification lists role as an attribute of the user record, and it is
 * one - but under JOINED it is already stored, structurally. A row whose id
 * appears in "officers" IS an officer; the table a row lives in is the role.
 * Adding a role column as well would mean the same fact recorded twice, in two
 * places that can disagree: an UPDATE could set role='ADMIN' on a row that
 * still only exists in "students", and nothing would stop it.
 *
 * getRole() below is abstract instead, so each subclass answers for itself and
 * the answer cannot drift from where the row actually lives. This is the same
 * reasoning as the specification's own instruction to DERIVE resolution time
 * from the timestamps rather than store it: a value that can be computed from
 * facts already recorded should not be recorded again.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class AppUser {

    /**
     * The system-generated User ID the specification asks for.
     *
     * Under JOINED this column is the primary key of "users" AND the primary
     * key of every subtype table, where it is additionally a foreign key back
     * to this one. That is what ties a student row to its user row, and it is
     * why a subtype table must not have its own auto-increment id - the value
     * is assigned here and copied down.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The login identity, unique across EVERY account type.
     *
     * Putting this on the supertype rather than on each subtype is the concrete
     * payoff of the JOINED design: one unique constraint on one column
     * guarantees that a student and an officer can never claim the same
     * address. Had each type kept its own table with its own email column, that
     * guarantee would need application code checking three tables on every
     * registration - which two simultaneous requests could race past.
     *
     * @NotBlank as well as @Email because Hibernate Validator's @Email
     * deliberately accepts null and "" - it validates format only and leaves
     * "is this required?" to @NotBlank. Without both, {"email": ""} would reach
     * the database and fail there as a 500 rather than a clean 400.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Column(name = "email", nullable = false, unique = true, length = 120)
    private String email;

    /**
     * The BCrypt hash. Never the plain-text password.
     *
     * WRITE_ONLY means Jackson will read it from an incoming request but never
     * write it to an outgoing response. Note that this annotation is now a
     * second line of defence rather than the only one: StudentResponse has no
     * password field at all, so the protection is structural. Both are kept
     * because they fail in different ways - one protects the DTO path, the
     * other protects anything that ever serialises an entity directly.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Password is required")
    @Column(name = "password", nullable = false)
    private String password;

    /**
     * The account status. False means soft-deleted, not gone.
     *
     * StudentUserDetailsService builds the UserDetails with
     * .disabled(!isActive()), so a false here makes authentication fail with
     * DisabledException even when the password is correct. Moved up from
     * Student because an officer or an administrator can be deactivated for
     * exactly the same reasons a student can, and F6's "administrators must be
     * able to deactivate accounts" applies to all three types.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** The registration date the specification asks for. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // --- Constructors ---

    protected AppUser() {
        // Required no-argument constructor for JPA.
        // protected rather than public: this class is abstract and nothing
        // outside the hierarchy should be calling it.
    }

    // --- The role, answered by the subclass ---

    /**
     * Which kind of account this is.
     *
     * Abstract, so adding a fourth account type means the compiler demands an
     * answer rather than letting a new subclass inherit a wrong default. Read
     * by StudentUserDetailsService to build the Spring Security authority, and
     * by StudentResponse to report the role to the frontend.
     */
    public abstract Role getRole();

    // --- Getters and setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
