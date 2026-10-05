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
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Base user record shared by Student, Officer and Administrator (login, status, created date).
 * JOINED inheritance: common columns live once in "users", each subtype table holds only its
 * own columns, so email is unique across all accounts and subtype columns can be NOT NULL.
 * There is no role column - the subtype table a row lives in is the role (see getRole()).
 * Named AppUser to avoid clashing with Spring Security's User class.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Email allows "" so @NotBlank is needed too; @Size matches the column length so a
    // long address gets a 400 instead of a database error.
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 120, message = "Email must be 120 characters or fewer")
    @Column(name = "email", nullable = false, unique = true, length = 120)
    private String email;

    // BCrypt hash only. WRITE_ONLY stops it ever being serialised in a response.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Password is required")
    @Column(name = "password", nullable = false)
    private String password;

    // false = suspended or removed; login is then refused with DisabledException.
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Set when an admin removes the account (final), null otherwise.
     * Suspended = active false, deletedAt null (reversible with the toggle).
     * The row is kept so old tickets and logs still show the person's name.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected AppUser() {
        // for JPA
    }

    // Abstract so every subtype has to say what it is; used to build the Spring Security authority.
    public abstract Role getRole();

    // Each subtype stores its name in its own column (fullName or displayName), so they answer here.
    public abstract String getDisplayName();

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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isRemoved() {
        return deletedAt != null;
    }

    /**
     * Removes the account and deactivates it in one step, so "removed but can still log in"
     * can't happen. Calling it again keeps the original removal time.
     */
    public void markRemoved() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
        this.active = false;
    }
}
