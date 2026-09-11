package com.helpdesk.common.reference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * SHARED REFERENCE DATA - not owned by any single feature.
 *
 * A ticket category: the subject a student picks when raising a request
 * ("Password & account access", "Module registration"). Each category belongs
 * to exactly one department, and that is what decides where a ticket is routed.
 *
 * Requirement specification, 3.2: "The system must associate every ticket with
 * exactly one category, and each category with exactly one department."
 *
 *
 * WHY THIS TABLE EXISTS AT ALL
 * ----------------------------
 * Ticket.category is currently a free-text String. That has three problems a
 * lookup table solves at once:
 *
 *   1. Nothing constrains the value. "IT", "I.T.", "it services" and a typo are
 *      all accepted, so any report that groups tickets by category is wrong.
 *   2. There is no route. A category string cannot tell the queue engine which
 *      department should receive the ticket, so F4 has nothing to build on.
 *   3. Renaming a category means an UPDATE across every historical ticket row,
 *      instead of changing one row here.
 *
 * Converting Ticket.category from String to a reference to this table is a
 * separate change in F2's own code and is NOT done here - this table is the
 * foundation that makes it possible, added without touching anyone else's file.
 *
 *
 * WHY THE PRIMARY KEY IS A GENERATED Long, UNLIKE Department
 * ----------------------------------------------------------
 * Department uses its natural code as the key because the university already
 * has stable codes for desks. Categories have no such external identifier -
 * they are created and renamed by administrators as support needs change. A
 * natural key here would mean the primary key is the category's NAME, so
 * renaming "Password reset" to "Password & account access" would change the
 * key, and every ticket pointing at it would have to be rewritten. A surrogate
 * key stays constant while the label moves, which is exactly what is wanted.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * unique = true across the whole table, not just within a department.
     *
     * This is the stricter of the two options and it is chosen deliberately. A
     * student picking from one flat dropdown should never see the same words
     * twice; if "Fee payment" appeared under both Financial Aid and
     * Registration, the student has no way to choose correctly and the routing
     * becomes a coin flip. If two desks genuinely handle overlapping subjects,
     * the fix is to name the categories distinctly ("Fee payment" and "Fee
     * refunds"), not to let identical labels coexist.
     */
    @NotBlank(message = "Category name is required")
    @Column(name = "name", length = 120, nullable = false, unique = true)
    private String name;

    /**
     * THIS IS THE FOREIGN KEY - and it is the point of this whole class.
     *
     * Every other relationship in the project so far is a plain Long field
     * (Ticket.studentId, Feedback.ticketId, Bookmark.folderId). Those compile
     * and run, but they generate NO foreign key constraint in the database: the
     * column is just a number, and nothing stops a row pointing at a parent
     * that does not exist. The requirement specification asks for the opposite
     * under Data Integrity - "no ticket, attachment, staff note, or feedback
     * record can reference a non-existent parent record."
     *
     * @ManyToOne with @JoinColumn is what makes Hibernate emit a real
     *     ALTER TABLE categories ADD CONSTRAINT ... FOREIGN KEY (department_code)
     *         REFERENCES departments (code)
     * so the DATABASE refuses a bad reference even if application code asks for
     * one. That guarantee holds against a buggy service method, a careless
     * manual UPDATE in a SQL console, and a second application connecting to the
     * same schema - none of which a Java-side check can cover.
     *
     * optional = false makes it NOT NULL: the specification says each category
     * belongs to exactly one department, so a category with no department is not
     * a meaningful row.
     *
     * FetchType.LAZY, not the @ManyToOne default of EAGER. EAGER means every
     * single query that loads a Category silently issues a second query for its
     * Department, whether or not the caller needs it - the N+1 problem. LAZY
     * defers that until someone actually calls getDepartment(). Because the
     * service layer converts entities to DTOs inside the transaction (see
     * ReferenceDataService), the department is loaded exactly when it is needed
     * and not otherwise.
     */
    @NotNull(message = "A category must belong to a department")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_code", nullable = false,
                foreignKey = @ForeignKey(name = "fk_category_department"))
    private Department department;

    /**
     * Soft-retirement, for the same reason Department has it: tickets reference
     * categories, so a category that is no longer offered must stop appearing in
     * the dropdown without the historical tickets losing what they were filed
     * under.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // --- Constructors ---

    public Category() {
        // Required no-argument constructor for JPA
    }

    public Category(String name, Department department) {
        this.name = name;
        this.department = department;
    }

    // --- Getters and setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
