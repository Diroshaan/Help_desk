package com.helpdesk.common.user.entity;

import com.helpdesk.common.reference.entity.Department;
import org.hibernate.annotations.ColumnDefault;
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
 * A help desk officer: works departmental queues, answers tickets and writes KB articles.
 * Officer-only columns can be NOT NULL because this table only holds officers (JOINED).
 * Accounts are created by admins in F6, so there is no service or controller here.
 */
@Entity
@Table(name = "officers")
@PrimaryKeyJoinColumn(name = "id")
public class Officer extends AppUser {

    // Kept as a String so leading zeros survive. No @Pattern: the spec gives no staff number format.
    @NotBlank(message = "Staff number is required")
    @Size(max = 20, message = "Staff number must be 20 characters or fewer")
    @Column(name = "staff_number", nullable = false, unique = true, length = 20)
    private String staffNumber;

    @NotBlank(message = "Job title is required")
    @Size(max = 100, message = "Job title must be 100 characters or fewer")
    @Column(name = "job_title", nullable = false, length = 100)
    private String jobTitle;

    @NotBlank(message = "An officer must have a full name")
    @Size(max = 120, message = "Full name must be 120 characters or fewer")
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /**
     * The admin who created this account. Nullable because older accounts have no record
     * and we don't want to invent one. LAZY since only the admin user screen needs it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provisioned_by",
            foreignKey = @ForeignKey(name = "fk_officer_provisioned_by"))
    private Administrator provisionedBy;

    // Departments this officer serves. Owning side, because queue scoping looks up an officer's departments.
    @ManyToMany
    @JoinTable(
        name = "officer_departments",
        joinColumns = @JoinColumn(name = "officer_id"),
        inverseJoinColumns = @JoinColumn(name = "department_code")
    )
    private Set<Department> departments = new HashSet<>();

    // Self-reference: each officer has at most one senior officer as supervisor (optional).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id", foreignKey = @ForeignKey(name = "fk_officer_supervisor"))
    private Officer supervisor;

    // Officer profile (US-04). One optional number, unlike students who can have several.
    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    @Column(name = "contact_number", length = 30)
    private String contactNumber;

    // Notifications are on by default. @ColumnDefault covers rows that already exist when the
    // column is added; without it MySQL would set them to false.
    @ColumnDefault("true")
    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @ColumnDefault("true")
    @Column(name = "portal_notifications_enabled", nullable = false)
    private boolean portalNotificationsEnabled = true;

    public Officer() {
        // for JPA
    }

    public Officer(String email, String password, String staffNumber, String jobTitle) {
        setEmail(email);
        setPassword(password);
        this.staffNumber = staffNumber;
        this.jobTitle = jobTitle;
    }

    // Use this one: the 4-argument constructor leaves fullName null, which the NOT NULL column rejects.
    public Officer(String email, String password, String staffNumber, String jobTitle, String fullName) {
        this(email, password, staffNumber, jobTitle);
        this.fullName = fullName;
    }

    @Override
    public Role getRole() {
        return Role.OFFICER;
    }

    @Override
    public String getDisplayName() {
        return fullName;
    }

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

    public Officer getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(Officer supervisor) {
        this.supervisor = supervisor;
    }

    public Set<Department> getDepartments() {
        return departments;
    }

    public void setDepartments(Set<Department> departments) {
        this.departments = departments;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    // blank is stored as null
    public void setContactNumber(String contactNumber) {
        this.contactNumber = (contactNumber == null || contactNumber.isBlank()) ? null : contactNumber.trim();
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
