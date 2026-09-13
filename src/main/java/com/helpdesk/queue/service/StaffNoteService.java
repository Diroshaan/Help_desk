package com.helpdesk.queue.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.queue.entity.StaffNote;
import com.helpdesk.queue.repository.StaffNoteRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Business logic for internal officer-only notes on a ticket. Notes are not
 * author-restricted for reading or deletion - StaffNote is department-shared
 * (visible to any officer working the ticket, see StaffNote.java), not a
 * personal scratchpad, so any officer scoped to the ticket may purge one.
 */
@Service
public class StaffNoteService {

    private final StaffNoteRepository staffNoteRepository;
    private final QueueService queueService;

    @Autowired
    public StaffNoteService(StaffNoteRepository staffNoteRepository, QueueService queueService) {
        this.staffNoteRepository = staffNoteRepository;
        this.queueService = queueService;
    }

    @Transactional
    public StaffNote create(Long officerId, Long ticketId, String note) {
        queueService.getQueuedTicket(officerId, ticketId);

        if (note == null || note.isBlank()) {
            throw new ValidationException("Note text is required");
        }

        StaffNote staffNote = new StaffNote();
        staffNote.setTicketId(ticketId);
        staffNote.setOfficerId(officerId);
        staffNote.setNote(note);
        return staffNoteRepository.save(staffNote);
    }

    @Transactional(readOnly = true)
    public List<StaffNote> listByTicket(Long officerId, Long ticketId) {
        queueService.getQueuedTicket(officerId, ticketId);
        return staffNoteRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    // Delete/purge
    @Transactional
    public void delete(Long officerId, Long ticketId, Long noteId) {
        queueService.getQueuedTicket(officerId, ticketId);

        StaffNote note = staffNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
        if (!note.getTicketId().equals(ticketId)) {
            throw new ResourceNotFoundException("Note not found");
        }
        staffNoteRepository.delete(note);
    }
}
