package com.helpdesk.queue.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Department affiliation for an officer account.
 *
 * There is no dedicated Officer/Admin account table yet - per the note on
 * Student.role, F6 owns building that out in a later sprint. Until then, an
 * officer's identity (name, email, login credentials, the "OFFICER" role
 * string) is a row in the students table, the same one everyone
 * authenticates against via /api/auth/login (see StudentUserDetailsService).
 *
 * This entity deliberately does NOT duplicate that identity data. It's an
 * extension record keyed by that same account id - id here is the same
 * value as the officer's row id in students, not a separately generated
 * key - that adds the one thing F4 actually needs and F1's table doesn't
 * have: which department the officer works. Every other F4 entity
 * (Ticket.assignedOfficerId, Resolution.officerId, StaffNote.officerId)
 * refers to an officer by this same account id, so there's a single
 * consistent identifier for "which officer" throughout. When F6 introduces
 * a proper Officer/Account entity, this table's id column becomes the
 * natural foreign key into it.
 */
@Entity
@Table(name = "officers")
public class Officer {

    @Id
    private Long id;

    @NotNull(message = "Department ID is required")
    @Column(nullable = false)
    private Long departmentId;

    private boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    //Constructors
    public Officer() {}    // Required no-argument constructor for JPA

    //Getters and setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getDepartmentId() {
        return departmentId;
    }
    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
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
