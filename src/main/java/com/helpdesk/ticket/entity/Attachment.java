package com.helpdesk.ticket.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A file a student uploads with a ticket, such as a screenshot or a log file.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Ticket ID is required")
    @Column(nullable = false)
    private Long ticketId;

    @NotBlank(message = "File name is required")
    @Column(nullable = false)
    private String fileName;

    @NotBlank(message = "File type is required")
    @Column(nullable = false)
    private String fileType;

    // MEDIUMBLOB (16 MB): a plain @Lob becomes TINYBLOB on MySQL, which only holds 255 bytes.
    @Column(name = "data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    private Long fileSize;

    private LocalDateTime uploadedAt = LocalDateTime.now();

    // Taken from the session in AttachmentService, never from the request.
    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    // Always SUBMISSION here; resolution files are stored on the resolution row.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", nullable = false, length = 20)
    @ColumnDefault("'SUBMISSION'")
    private AttachmentKind kind = AttachmentKind.SUBMISSION;

    public Attachment() {}

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
    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public String getFileType() {
        return fileType;
    }
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
    public byte[] getData() {
        return data;
    }
    public void setData(byte[] data) {
        this.data = data;
    }
    public Long getFileSize() {
        return fileSize;
    }
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }
    public void setUploadedByUserId(Long uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }
    public AttachmentKind getKind() {
        return kind;
    }
    public void setKind(AttachmentKind kind) {
        this.kind = kind;
    }
}
