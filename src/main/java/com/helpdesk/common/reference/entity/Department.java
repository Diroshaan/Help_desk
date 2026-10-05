package com.helpdesk.common.reference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/**
 * A support department (help desk) that tickets are routed to, e.g. "IT Services".
 * The key is the natural department code, as the spec asks; codes are short, stable and
 * readable. Not the same as Student.department, which is the student's faculty.
 */
@Entity
@Table(name = "departments")
public class Department {

    // Typed by a person, so @Pattern stops "it", " IT" and "IT " becoming separate departments.
    @Id
    @NotBlank(message = "Department code is required")
    @Pattern(regexp = "^[A-Z]{2,10}$",
             message = "Department code must be 2-10 uppercase letters, e.g. IT or REG")
    @Size(max = 10, message = "Department code must be 10 characters or fewer")
    @Column(name = "code", length = 10, nullable = false)
    private String code;

    // Unique, so two desks can't appear with the same name in the dropdown.
    @NotBlank(message = "Department name is required")
    @Size(max = 100, message = "Department name must be 100 characters or fewer")
    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    // Optional - a new department can take tickets before it has published contact details.
    @Email(message = "Must be a valid email address")
    @Size(max = 120, message = "Contact email must be 120 characters or fewer")
    @Column(name = "contact_email", length = 120)
    private String contactEmail;

    @Size(max = 30, message = "Contact phone must be 30 characters or fewer")
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    // Departments are retired, not deleted: tickets, categories and articles still point at them.
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Department() {
        // for JPA
    }

    public Department(String code, String name, String contactEmail, String contactPhone) {
        this.code = code;
        this.name = name;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
    }

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
