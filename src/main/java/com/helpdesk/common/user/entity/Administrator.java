package com.helpdesk.common.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SHARED USER MODEL - not owned by any single feature.
 *
 * A System Administrator: provisions privileged accounts, manages roles and
 * permissions, publishes announcements and sees system-wide analytics
 * (requirement specification section 2, Platform Managers).
 *
 *
 * WHY THIS CLASS EXISTS WHEN IT ADDS ALMOST NOTHING
 * -------------------------------------------------
 * An administrator has one attribute of its own - a staff number - and a
 * reasonable objection is that this could have been an Officer with a different
 * role, or a flag on the user record.
 *
 * It is a separate type because the specification says the three kinds of actor
 * are disjoint - "each user as exactly one of Student, Help Desk Officer or
 * System Administrator" - and because the two are not the same thing with
 * different permissions. An officer belongs to departments and owns tickets; an
 * administrator does neither, and provisions the people who do. Modelling an
 * administrator as an officer would mean every officer query has to remember to
 * exclude the administrators, and every such query is one forgotten filter away
 * from routing a ticket to somebody who does not work a queue.
 *
 * The specification also requires recording "the administrator who provisioned
 * each privileged account" and "the administrator who published each
 * announcement" - both foreign keys that need a table to point at. Those
 * relationships belong to F6 and are not built here.
 *
 * A thin table is the honest answer when a type genuinely has little of its
 * own. Its value is in what it makes impossible, not in how many columns it
 * has.
 */
@Entity
@Table(name = "administrators")
@PrimaryKeyJoinColumn(name = "id")
public class Administrator extends AppUser {

    /**
     * Staff identification number.
     *
     * Nullable here, unlike on Officer, and the difference is deliberate. Every
     * help desk officer is a member of support staff with a number issued to
     * them. The first administrator account in a new deployment is a technical
     * bootstrap account created before anybody has been issued anything - and
     * requiring a number would mean inventing a fake one to get the system
     * started, which is worse than an empty column.
     *
     * Still unique when present: two administrators cannot share a number.
     * A unique constraint in SQL permits multiple NULLs, so "optional but
     * unique when set" is exactly what this expresses, and is the reason the
     * constraint does not have to be given up to make the column optional.
     */
    @Size(max = 20, message = "Staff number must be 20 characters or fewer")
    @Column(name = "staff_number", unique = true, length = 20)
    private String staffNumber;

    /**
     * A human-readable name for the account, e.g. "Registry Systems Admin".
     *
     * AppUser deliberately holds no name: a student's name is split into
     * components for the specification's reasons, and an administrator's is
     * usually an operational label rather than a person. Keeping each subtype's
     * naming on the subtype is what "role-specific data held only against the
     * relevant type" means in practice.
     */
    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must be 100 characters or fewer")
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /**
     * The administrator who created this administrator account.
     *
     * SELF-REFERENCING: both ends of this association are rows in the same
     * table. That is legitimate and it is one of the relationship patterns the
     * requirement specification asks the model to demonstrate - a second,
     * naturally occurring one alongside F5's related-articles link.
     *
     * Nullable for the reason given on Officer.provisionedBy, plus one that is
     * specific to this table and is the clearest case in the whole model: the
     * very first administrator on a new deployment is created by
     * AdminBootstrapSeeder precisely because no administrator exists yet to
     * create it. That row's provisioned_by is null permanently. A NOT NULL
     * column would make the seeder impossible to write without inserting a row
     * that points at itself - which would record that the first administrator
     * created itself, a claim that is simply false.
     *
     * LAZY, and a named foreign key, for the same reasons as on Officer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provisioned_by",
            foreignKey = @ForeignKey(name = "fk_administrator_provisioned_by"))
    private Administrator provisionedBy;

    // --- Constructors ---

    public Administrator() {
        // Required no-argument constructor for JPA
    }

    public Administrator(String email, String password, String displayName) {
        setEmail(email);
        setPassword(password);
        this.displayName = displayName;
    }

    // --- Role ---

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }

    // --- Getters and setters ---

    public String getStaffNumber() {
        return staffNumber;
    }

    public void setStaffNumber(String staffNumber) {
        this.staffNumber = staffNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Administrator needs no override of AppUser.getDisplayName(): the getter
     * immediately above already has the right name and the right signature, so
     * the compiler treats it as the implementation. That is not a coincidence to
     * be tidied away - it is the reason getDisplayName() was the name chosen for
     * the abstract method rather than getName() or getLabel().
     */

    public Administrator getProvisionedBy() {
        return provisionedBy;
    }

    public void setProvisionedBy(Administrator provisionedBy) {
        this.provisionedBy = provisionedBy;
    }
}
