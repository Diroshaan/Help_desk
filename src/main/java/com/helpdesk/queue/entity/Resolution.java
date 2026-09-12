package com.helpdesk.queue.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * An officer's official solution to a ticket. One per ticket (ticketId is
 * unique) rather than a revision history table - editing a resolution
 * overwrites responseText/attachment and bumps updatedAt, which is enough
 * for the current requirements without a separate audit table.
 *
 * ticketId and officerId are kept as plain Long references, matching the
 * pattern used by Ticket.studentId, instead of @ManyToOne relations.
 *
 * The optional attachment fields mirror ticket.entity.Attachment's shape
 * rather than reusing that entity directly: Attachment.ticketId is
 * NOT NULL and it isn't generalized for a second, unrelated owner
 * (an officer's solution file vs. a student's upload), so bolting a
 * resolution attachment onto it would mean either a fake ticketId-only
 * link or loosening a constraint F2 depends on.
 */
@Entity
@Table(name = "resolutions")
public class Resolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Ticket ID is required")
    @Column(nullable = false, unique = true)
    private Long ticketId;

    @NotNull(message = "Officer ID is required")
    @Column(nullable = false)
    private Long officerId;

    @NotBlank(message = "Response text is required")
    @Column(nullable = false, length = 4000)
    private String responseText;

    private String attachmentFileName;

    private String attachmentFileType;

    @Lob
    private byte[] attachmentData;

    private Long attachmentFileSize;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    //Constructors
    public Resolution() {}    // Required no-argument constructor for JPA

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
    public String getResponseText() {
        return responseText;
    }
    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }
    public String getAttachmentFileName() {
        return attachmentFileName;
    }
    public void setAttachmentFileName(String attachmentFileName) {
        this.attachmentFileName = attachmentFileName;
    }
    public String getAttachmentFileType() {
        return attachmentFileType;
    }
    public void setAttachmentFileType(String attachmentFileType) {
        this.attachmentFileType = attachmentFileType;
    }
    public byte[] getAttachmentData() {
        return attachmentData;
    }
    public void setAttachmentData(byte[] attachmentData) {
        this.attachmentData = attachmentData;
    }
    public Long getAttachmentFileSize() {
        return attachmentFileSize;
    }
    public void setAttachmentFileSize(Long attachmentFileSize) {
        this.attachmentFileSize = attachmentFileSize;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
