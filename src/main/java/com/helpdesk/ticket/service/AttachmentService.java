package com.helpdesk.ticket.service;

import com.helpdesk.common.settings.HelpdeskSettings;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.files.FileTypeDetector;
import com.helpdesk.ticket.dto.AttachmentResponse;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.entity.AttachmentKind;
import com.helpdesk.ticket.repository.AttachmentRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Files attached to a ticket. Ownership checks are left to TicketService so
 * there is only one place that decides whether a ticket belongs to a student.
 */
@Service
public class AttachmentService {

    // Only PDFs and images are allowed (see FileTypeDetector). No SVG, since it can contain scripts.
    private static final long MAX_FILE_SIZE = HelpdeskSettings.getInstance().getMaxUploadBytes();

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;

    @Autowired
    public AttachmentService(AttachmentRepository attachmentRepository, TicketService ticketService) {
        this.attachmentRepository = attachmentRepository;
        this.ticketService = ticketService;
    }

    // Only while the ticket is still OPEN.
    public Attachment upload(Long studentId, Long ticketId, MultipartFile file) {
        ticketService.getOwnedOpenTicket(ticketId, studentId);

        if (file == null || file.isEmpty()) {
            throw new ValidationException("Attachment file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File exceeds the 5MB limit");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        // Check the file's first bytes, not the Content-Type the browser sent,
        // so a renamed .exe can't pass as an image.
        String detectedType = FileTypeDetector.detect(bytes, FileTypeDetector.ATTACHMENT_TYPES)
                .orElseThrow(() -> new ValidationException(
                        "Only PDF and image files (PNG, JPEG, GIF, WEBP) are allowed"));

        String fileName = file.getOriginalFilename();

        Attachment attachment = new Attachment();
        attachment.setTicketId(ticketId);
        attachment.setFileName((fileName == null || fileName.isBlank()) ? "attachment" : fileName);
        attachment.setFileType(detectedType);
        attachment.setFileSize(file.getSize());
        attachment.setData(bytes);
        attachment.setUploadedByUserId(studentId);
        attachment.setKind(AttachmentKind.SUBMISSION);

        return attachmentRepository.save(attachment);
    }

    // Metadata only; the bytes are loaded one file at a time on download.
    @Transactional(readOnly = true)
    public List<AttachmentResponse> listByTicket(Long studentId, Long ticketId) {
        ticketService.getOwnedTicket(ticketId, studentId);
        return attachmentRepository.findMetadataByTicketId(ticketId);
    }

    @Transactional(readOnly = true)
    public Attachment getOwnedAttachment(Long studentId, Long ticketId, Long attachmentId) {
        ticketService.getOwnedTicket(ticketId, studentId);
        return findByIdAndTicketId(attachmentId, ticketId);
    }

    // Officer-side reads. No ownership check here: the queue checks the
    // officer's department before calling these.
    @Transactional(readOnly = true)
    public List<AttachmentResponse> listForTicket(Long ticketId) {
        return attachmentRepository.findMetadataByTicketId(ticketId);
    }

    // Still 404 if the file isn't on this ticket, so access to one ticket
    // can't be used to read another ticket's files.
    @Transactional(readOnly = true)
    public Attachment getForTicket(Long ticketId, Long attachmentId) {
        return findByIdAndTicketId(attachmentId, ticketId);
    }

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
