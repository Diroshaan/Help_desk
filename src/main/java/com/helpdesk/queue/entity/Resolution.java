package com.helpdesk.queue.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * An officer's solution to a ticket. One per ticket - editing it overwrites the
 * text and file. The optional file is stored on this row, not in attachments.
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

    // MEDIUMBLOB: a plain @Lob is TINYBLOB (255 bytes) on MySQL.
    @Column(name = "attachment_data", columnDefinition = "MEDIUMBLOB")
    private byte[] attachmentData;

    private Long attachmentFileSize;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    public Resolution() {}

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
