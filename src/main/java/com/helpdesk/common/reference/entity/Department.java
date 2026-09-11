package com.helpdesk.common.reference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * A support department: the desk a ticket is routed to and eventually answered
 * by. "IT Services", "Registration", "Financial Aid".
 *
 *
 * WHY THIS LIVES IN common.reference AND NOT IN A FEATURE PACKAGE
 * ---------------------------------------------------------------
 * Four features need this table and none of them owns it. F2 needs it so a
 * student can pick where their ticket goes. F4 needs it because an officer's
 * queue IS a department. F5 needs it to file knowledge base articles. F6 needs
 * it to report ticket volume per desk. Putting it inside any one of those
 * packages would force the other three to depend on that feature's package,
 * which is backwards - shared foundations should be depended ON, not depend on
 * the things above them. common.reference is the honest home for it.
 *
 *
 * WHY THE PRIMARY KEY IS A STRING CODE AND NOT A GENERATED Long
 * -------------------------------------------------------------
 * Every other entity in this project uses @GeneratedValue Long ids, so this is
 * a deliberate exception, for two reasons.
 *
 * First, the requirement specification asks for it directly: "The system must
 * store each department with a unique Department Code (primary key)."
 *
 * Second, and more usefully, this is a NATURAL KEY. A department code is a real
 * identifier the university already uses - it is short, stable, and meaningful
 * on sight. A surrogate Long would mean every query joining tickets to
 * departments returns rows saying "department 3", which tells a human nothing,
 * and it would let two rows both named "IT Services" exist with different ids.
 * Reference tables with a small, fixed, human-meaningful set of rows are the
 * textbook case where a natural key is the better choice. Tickets and students,
 * where rows are created constantly by users and have no stable external
 * identifier, are the textbook case for the opposite.
 *
 *
 * NOT TO BE CONFUSED WITH Student.department
 * ------------------------------------------
 * Student.department holds the student's FACULTY ("Faculty of Computing") - an
 * academic grouping the student belongs to. This entity is a SUPPORT DESK that
 * answers tickets. They are unrelated concepts that unfortunately share a word.
 * Student.department should be renamed to 'faculty' to remove the ambiguity;
 * that rename touches the registration form, the profile page and both DTOs, so
 * it is deliberately left as a separate piece of work rather than smuggled into
 * this one.
 */
@Entity
@Table(name = "departments")
public class Department {

    /**
     * The natural primary key: a short uppercase code such as "IT" or "REG".
     *
     * @Pattern is not decoration here. Because this key is typed by a human
     * rather than generated, nothing else stops "it", " IT" and "IT " becoming
     * three different departments that look identical in the UI. Constraining
     * the format at the point of entry is the only cheap defence.
     *
     * length = 10 rather than the default 255: a primary key is copied into
     * every foreign key column that references it and into the index behind
     * each one, so an oversized key column is paid for many times over.
     */
    @Id
    @NotBlank(message = "Department code is required")
    @Pattern(regexp = "^[A-Z]{2,10}$",
             message = "Department code must be 2-10 uppercase letters, e.g. IT or REG")
    @Column(name = "code", length = 10, nullable = false)
    private String code;

    /**
     * The human-readable name shown in dropdowns and on the landing page.
     *
     * unique = true as well as the code: two desks may not share a display
     * name. Without this, an administrator could create "IT" and "ITS" both
     * called "IT Services", and a student picking from a dropdown would have no
     * way to tell which one their ticket went to.
     */
    @NotBlank(message = "Department name is required")
    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    /**
     * Contact details for the desk.
     *
     * These currently live as a hardcoded DESKS array in Welcome.jsx. Moving
     * them into the database is the point of a reference table: when the IT
     * help desk changes its phone number, that is a data change an administrator
     * makes, not a code change that requires a rebuild and a redeploy.
     *
     * Nullable on purpose - a newly created department is useful for routing
     * before anyone has published a phone number for it, and forcing a
     * placeholder value in would be worse than an empty field.
     */
    @Email(message = "Must be a valid email address")
    @Column(name = "contact_email", length = 120)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    /**
     * Soft-retirement flag, mirroring Student.active.
     *
     * A department that stops operating cannot simply be deleted: tickets,
     * categories and articles point at it, and the foreign keys this design
     * introduces will (correctly) refuse the delete. Even if they did not,
     * deleting would destroy the history of every ticket that desk ever handled.
     * Marking it inactive hides it from the "where should this ticket go?"
     * dropdown while every existing record stays readable and intact.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // --- Constructors ---

    public Department() {
        // Required no-argument constructor for JPA
    }

    public Department(String code, String name, String contactEmail, String contactPhone) {
        this.code = code;
        this.name = name;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
    }

    // --- Getters and setters ---

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
