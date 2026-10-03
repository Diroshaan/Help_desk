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
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Business logic for an officer's official solution to a ticket. Ticket
 * ownership/department scoping is delegated to QueueService.getQueuedTicket
 * rather than re-checked here, same pattern as AttachmentService delegating
 * to TicketService.
 *
 * "Finalized" / "final closure" (create is always allowed while IN_PROGRESS;
 * edit and revoke are blocked once it has happened) is defined here as: the
 * student has left feedback on the ticket. ticketportal.entity.Feedback can
 * only be submitted for a RESOLVED ticket (see FeedbackService), and once a
 * rating exists for a specific resolution, silently changing or pulling that
 * resolution out from under it would make the feedback refer to a solution
 * that's no longer there. FeedbackRepository.existsByTicketId is the check.
 */
@Service
public class ResolutionService {

    // Same limits as AttachmentService (ticket.service) - not extracted to a
    // shared constant, since the two packages don't currently share any
    // utility class and duplicating two constants is cheaper than
    // introducing one for this alone.
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

    // Create - also moves the ticket to RESOLVED and stamps resolvedAt.
    // Only while IN_PROGRESS: a ticket must be claimed (OPEN -> IN_PROGRESS,
    // see QueueService.updateStatus) before it can be resolved, keeping the
    // OPEN -> IN_PROGRESS -> RESOLVED order intact end to end.
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

    // Read
    @Transactional(readOnly = true)
    public Resolution getByTicketId(Long officerId, Long ticketId) {
        queueService.getQueuedTicket(officerId, ticketId);
        return findByTicketId(ticketId);
    }

    // Used by the queue detail endpoint, where "no resolution yet" is a
    // normal, non-error state rather than a 404.
    @Transactional(readOnly = true)
    public Optional<Resolution> findByTicketIdOptional(Long officerId, Long ticketId) {
        queueService.getQueuedTicket(officerId, ticketId);
        return resolutionRepository.findByTicketId(ticketId);
    }

    // Update - blocked once the student has left feedback (see class comment)
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

    // Revoke - deletes the resolution and reopens the ticket to IN_PROGRESS.
    // Same finalization guard as edit.
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

    // Only the officer who wrote the answer (or that officer's supervisor) may
    // change it; otherwise the response would still credit the original author
    // with words they never wrote.
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
        // The type comes from the file's own bytes, never from the client's label.
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
