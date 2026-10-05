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
 * A system administrator: provisions officer/admin accounts and sees system-wide analytics.
 * Kept as its own type (not an Officer with a flag) because the spec says the three user
 * types are disjoint, and officer queries never have to filter admins out.
 */
@Entity
@Table(name = "administrators")
@PrimaryKeyJoinColumn(name = "id")
public class Administrator extends AppUser {

    // Optional (the first bootstrap admin has none) but unique when set; SQL allows many NULLs.
    @Size(max = 20, message = "Staff number must be 20 characters or fewer")
    @Column(name = "staff_number", unique = true, length = 20)
    private String staffNumber;

    // Often a label like "Registry Systems Admin" rather than a person's name.
    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must be 100 characters or fewer")
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /**
     * Self-reference: the admin who created this admin. Null for the first admin made by
     * AdminBootstrapSeeder, since nobody existed yet to create it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provisioned_by",
            foreignKey = @ForeignKey(name = "fk_administrator_provisioned_by"))
    private Administrator provisionedBy;

    public Administrator() {
        // for JPA
    }

    public Administrator(String email, String password, String displayName) {
        setEmail(email);
        setPassword(password);
        this.displayName = displayName;
    }

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }

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

    // getDisplayName() above also implements AppUser.getDisplayName().

    public Administrator getProvisionedBy() {
        return provisionedBy;
    }

    public void setProvisionedBy(Administrator provisionedBy) {
        this.provisionedBy = provisionedBy;
    }
}
