package com.helpdesk.queue.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * An internal note an officer leaves on a ticket, never shown to the
 * student - only to officers working the queue. Many-to-one with Ticket,
 * kept as a plain ticketId Long reference (matching Ticket.studentId)
 * rather than a JPA relation.
 */
@Entity
@Table(name = "staff_notes")
public class StaffNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Ticket ID is required")
    @Column(nullable = false)
    private Long ticketId;

    @NotNull(message = "Officer ID is required")
    @Column(nullable = false)
    private Long officerId;

    @NotBlank(message = "Note text is required")
    @Column(nullable = false, length = 2000)
    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();

    //Constructors
    public StaffNote() {}    // Required no-argument constructor for JPA

    //Getters and setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getTicketId() {
        return ticketId;
    }
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    public Long getOfficerId() {
        return officerId;
    }
    public void setOfficerId(Long officerId) {
        this.officerId = officerId;
    }
    public String getNote() {
        return note;
    }
    public void setNote(String note) {
        this.note = note;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
