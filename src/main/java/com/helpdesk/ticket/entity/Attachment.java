package com.helpdesk.ticket.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * F2 - Advanced Ticket Request Engine
 *
 * A file uploaded in support of a ticket (e.g. a screenshot or log file).
 *
 * ticketId is kept as a plain Long reference (not @ManyToOne), matching the
 * same pattern used by Ticket.studentId, so this stays decoupled from the
 * Ticket entity.
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

    // MEDIUMBLOB, not a bare @Lob: on MySQL Hibernate maps @Lob byte[] to
    // TINYBLOB, which holds only 255 bytes, so every real file failed on Aiven
    // while passing on H2 (the same trap F1 hit with Student.profilePicture).
    // MEDIUMBLOB holds 16 MB, comfortably above our 5 MB upload limit.
    @Column(name = "data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    private Long fileSize;

    private LocalDateTime uploadedAt = LocalDateTime.now();

    // #44, contract C8: who uploaded this file. Set from the SESSION in
    // AttachmentService.upload, never from the request - the same
    // mass-assignment protection TicketCreateRequest uses for studentId.
    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    // RESOLUTION is reserved; resolution files stay on the resolutions row
    // (F4 keeps its own columns - contract C8), so every attachment created
    // through this package is SUBMISSION. @JdbcTypeCode(VARCHAR): the same
    // ENUM-column trap as Ticket.status - see that field's comment.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", nullable = false, length = 20)
    @ColumnDefault("'SUBMISSION'")
    private AttachmentKind kind = AttachmentKind.SUBMISSION;

    //Constructors
    public Attachment() {}    // Required no-argument constructor for JPA

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
