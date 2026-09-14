package com.helpdesk.ticket.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.repository.AttachmentRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

/**
 * F2 - Advanced Ticket Request Engine (Chamikara A. K, IT25102416)
 *
 * Business logic for files a student attaches to their own ticket (e.g. a
 * screenshot or log file). Ownership of the ticket is delegated to
 * TicketService.getOwnedTicket/getOwnedOpenTicket rather than re-checked
 * here, so there is one place that decides "does this ticket belong to this
 * student" (and, for upload/delete, "is it still open").
 */
@Service
public class AttachmentService {

    // NFR 5.1: attachments are restricted to PDF and standard image formats
    // only. An explicit allow-list, not a startsWith("image/") check - that
    // would also admit image/svg+xml, and an SVG can carry embedded
    // JavaScript that executes when a browser renders it.
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp");

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;

    @Autowired
    public AttachmentService(AttachmentRepository attachmentRepository, TicketService ticketService) {
        this.attachmentRepository = attachmentRepository;
        this.ticketService = ticketService;
    }

    // Create - only while the ticket is still OPEN (draft-stage requests only)
    public Attachment upload(Long studentId, Long ticketId, MultipartFile file) {
        ticketService.getOwnedOpenTicket(ticketId, studentId);

        if (file == null || file.isEmpty()) {
            throw new ValidationException("Attachment file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File exceeds the 5MB limit");
        }
        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ValidationException("Only PDF and image files (PNG, JPEG, GIF, WEBP) are allowed");
        }

        String fileName = file.getOriginalFilename();

        Attachment attachment = new Attachment();
        attachment.setTicketId(ticketId);
        attachment.setFileName((fileName == null || fileName.isBlank()) ? "attachment" : fileName);
        attachment.setFileType(file.getContentType());
        attachment.setFileSize(file.getSize());
        try {
            attachment.setData(file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        return attachmentRepository.save(attachment);
    }

    // Read
    public List<Attachment> listByTicket(Long studentId, Long ticketId) {
        ticketService.getOwnedTicket(ticketId, studentId);
        return attachmentRepository.findByTicketId(ticketId);
    }

    public Attachment getOwnedAttachment(Long studentId, Long ticketId, Long attachmentId) {
        ticketService.getOwnedTicket(ticketId, studentId);
        return findByIdAndTicketId(attachmentId, ticketId);
    }

    // Delete - only while the ticket is still OPEN
    public void delete(Long studentId, Long ticketId, Long attachmentId) {
        ticketService.getOwnedOpenTicket(ticketId, studentId);
        Attachment attachment = findByIdAndTicketId(attachmentId, ticketId);
        attachmentRepository.delete(attachment);
    }

    private Attachment findByIdAndTicketId(Long attachmentId, Long ticketId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        if (!attachment.getTicketId().equals(ticketId)) {
            throw new ResourceNotFoundException("Attachment not found");
        }
        return attachment;
    }
}
