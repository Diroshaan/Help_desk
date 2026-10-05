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
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

/**
 * A ticket category (e.g. "Module registration"). Each category belongs to exactly one
 * department, which decides where a ticket is routed. Uses a generated id rather than the
 * name as key, so a category can be renamed without touching tickets.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique across all departments so the student never sees the same label twice in the dropdown.
    @NotBlank(message = "Category name is required")
    @Size(max = 120, message = "Category name must be 120 characters or fewer")
    @Column(name = "name", length = 120, nullable = false, unique = true)
    private String name;

    /**
     * Real foreign key to departments, so the database itself rejects a category pointing at a
     * department that doesn't exist. Required (one department each). LAZY to avoid N+1.
     */
    @NotNull(message = "A category must belong to a department")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_code", nullable = false,
                foreignKey = @ForeignKey(name = "fk_category_department"))
    private Department department;

    // Retired categories leave the dropdown but old tickets keep their category.
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Category() {
        // for JPA
    }

    public Category(String name, Department department) {
        this.name = name;
        this.department = department;
    }

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
