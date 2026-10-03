package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.queue.entity.Resolution;
import com.helpdesk.queue.repository.ResolutionRepository;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticketportal.dto.TicketResolutionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F3 - lets a student read the officer's answer to their own ticket.
 *
 * Read-only use of F4's ResolutionRepository, agreed as contract C5. Every
 * read in ResolutionService demands an officer serving the ticket's
 * department, so it can't be reused for students; here the ownership check
 * (TicketService.getOwnedTicket) is the access control instead.
 */
@Service
public class TicketResolutionService {

    private final TicketService ticketService;
    private final ResolutionRepository resolutionRepository;
    private final OfficerRepository officerRepository;

    @Autowired
    public TicketResolutionService(TicketService ticketService,
                                    ResolutionRepository resolutionRepository,
                                    OfficerRepository officerRepository) {
        this.ticketService = ticketService;
        this.resolutionRepository = resolutionRepository;
        this.officerRepository = officerRepository;
    }

    // 404 for a missing ticket AND for someone else's ticket - a 403 would
    // confirm to the caller that the ticket id exists.
    @Transactional(readOnly = true)
    public TicketResolutionResponse getForStudent(Long ticketId, Long studentId) {
        Resolution resolution = findOwnedResolution(ticketId, studentId);

        String officerName = officerRepository.findById(resolution.getOfficerId())
                .map(Officer::getFullName)
                .orElse("Help desk officer");

        // Built inside the transaction, so nothing lazy is touched after it closes.
        return new TicketResolutionResponse(
                resolution.getId(),
                resolution.getResponseText(),
                officerName,
                resolution.getAttachmentFileName(),
                resolution.getAttachmentFileSize(),
                resolution.getCreatedAt(),
                resolution.getUpdatedAt());
    }

    // Same checks as getForStudent, plus 404 when the answer has no file.
    @Transactional(readOnly = true)
    public Resolution getForStudentWithFile(Long ticketId, Long studentId) {
        Resolution resolution = findOwnedResolution(ticketId, studentId);
        if (resolution.getAttachmentData() == null || resolution.getAttachmentFileName() == null) {
            throw new ResourceNotFoundException("This answer has no attachment");
        }
        return resolution;
    }

    private Resolution findOwnedResolution(Long ticketId, Long studentId) {
        ticketService.getOwnedTicket(ticketId, studentId);
        return resolutionRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("This ticket has no answer yet"));
    }
}
