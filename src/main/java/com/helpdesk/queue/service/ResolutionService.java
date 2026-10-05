package com.helpdesk.queue.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.files.FileTypeDetector;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.queue.entity.Resolution;
import com.helpdesk.queue.repository.ResolutionRepository;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.entity.TicketStatus;
import com.helpdesk.ticketportal.repository.FeedbackRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * An officer's resolution for a ticket. Access checks are done by QueueService.
 * Once the student has left feedback the resolution is final and can no longer
 * be edited or revoked, so the feedback always matches the answer it rated.
 */
@Service
public class ResolutionService {

    // Same limit as student attachments.
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final ResolutionRepository resolutionRepository;
    private final QueueService queueService;
    private final FeedbackRepository feedbackRepository;
    private final OfficerRepository officerRepository;

    @Autowired
    public ResolutionService(ResolutionRepository resolutionRepository, QueueService queueService,
                              FeedbackRepository feedbackRepository, OfficerRepository officerRepository) {
        this.resolutionRepository = resolutionRepository;
        this.queueService = queueService;
        this.feedbackRepository = feedbackRepository;
        this.officerRepository = officerRepository;
    }

    // Also marks the ticket RESOLVED. Only allowed while IN_PROGRESS, so a
    // ticket has to be picked up before it can be resolved.
    @Transactional
    public Resolution create(Long officerId, Long ticketId, String responseText, MultipartFile attachment) {
        Ticket ticket = queueService.getWorkableTicket(officerId, ticketId);
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new ValidationException("A ticket can only be resolved while it is in progress");
        }
        if (resolutionRepository.findByTicketId(ticketId).isPresent()) {
            throw new DuplicateResourceException("This ticket already has a resolution - edit it instead");
        }

        Resolution resolution = new Resolution();
        resolution.setTicketId(ticketId);
        resolution.setOfficerId(officerId);
        applyResponseText(resolution, responseText);
        applyAttachment(resolution, attachment);
        resolution = resolutionRepository.save(resolution);

        queueService.markResolved(ticket, officerId);
        return resolution;
    }

    @Transactional(readOnly = true)
    public Resolution getByTicketId(Long officerId, Long ticketId) {
        queueService.getQueuedTicket(officerId, ticketId);
        return findByTicketId(ticketId);
    }

    // For the detail view, where no resolution yet is normal rather than a 404.
    @Transactional(readOnly = true)
    public Optional<Resolution> findByTicketIdOptional(Long officerId, Long ticketId) {
        queueService.getQueuedTicket(officerId, ticketId);
        return resolutionRepository.findByTicketId(ticketId);
    }

    @Transactional
    public Resolution edit(Long officerId, Long ticketId, String responseText, MultipartFile attachment) {
        queueService.getWorkableTicket(officerId, ticketId);
        Resolution resolution = findByTicketId(ticketId);
        requireAuthorOrSupervisor(resolution, officerId);
        requireNotFinalized(ticketId);

        applyResponseText(resolution, responseText);
        if (attachment != null && !attachment.isEmpty()) {
            applyAttachment(resolution, attachment);
        }
        return resolutionRepository.save(resolution);
    }

    // Deletes the resolution and puts the ticket back to IN_PROGRESS.
    @Transactional
    public void revoke(Long officerId, Long ticketId) {
        Ticket ticket = queueService.getWorkableTicket(officerId, ticketId);
        Resolution resolution = findByTicketId(ticketId);
        requireAuthorOrSupervisor(resolution, officerId);
        requireNotFinalized(ticketId);

        resolutionRepository.delete(resolution);
        queueService.reopen(ticket, officerId);
    }

    private Resolution findByTicketId(Long ticketId) {
        return resolutionRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Resolution not found"));
    }

    // Only the author or their supervisor may edit or revoke, since the answer
    // is still credited to the author.
    private void requireAuthorOrSupervisor(Resolution resolution, Long officerId) {
        if (resolution.getOfficerId().equals(officerId)) {
            return;
        }
        boolean isSupervisor = officerRepository.findById(resolution.getOfficerId())
                .map(Officer::getSupervisor)
                .map(supervisor -> supervisor.getId().equals(officerId))
                .orElse(false);
        if (!isSupervisor) {
            throw new ValidationException("Only the officer who wrote this answer can change it");
        }
    }

    private void requireNotFinalized(Long ticketId) {
        if (feedbackRepository.existsByTicketId(ticketId)) {
            throw new ValidationException(
                    "This ticket already has student feedback and its resolution can no longer be "
                            + "edited or revoked");
        }
    }

    private void applyResponseText(Resolution resolution, String responseText) {
        if (responseText == null || responseText.isBlank()) {
            throw new ValidationException("Response text is required");
        }
        resolution.setResponseText(responseText);
    }

    private void applyAttachment(Resolution resolution, MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            return;
        }
        if (attachment.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File exceeds the 5MB limit");
        }

        byte[] bytes;
        try {
            bytes = attachment.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
        // Type is taken from the file's bytes, not the client's label.
        String type = FileTypeDetector.detect(bytes, FileTypeDetector.ATTACHMENT_TYPES)
                .orElseThrow(() -> new ValidationException(
                        "Only PDF and image files (PNG, JPEG, GIF, WEBP) are allowed"));

        String fileName = attachment.getOriginalFilename();
        resolution.setAttachmentFileName((fileName == null || fileName.isBlank()) ? "attachment" : fileName);
        resolution.setAttachmentFileType(type);
        resolution.setAttachmentFileSize(attachment.getSize());
        resolution.setAttachmentData(bytes);
    }
}
