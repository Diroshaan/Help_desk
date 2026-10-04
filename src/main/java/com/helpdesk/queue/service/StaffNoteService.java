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
 * Internal staff notes on a ticket. Notes are shared within the department, so
 * any officer who can work the ticket may read or delete them, not just the author.
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
        queueService.getWorkableTicket(officerId, ticketId);

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

    @Transactional
    public void delete(Long officerId, Long ticketId, Long noteId) {
        queueService.getWorkableTicket(officerId, ticketId);

        StaffNote note = staffNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
        if (!note.getTicketId().equals(ticketId)) {
            throw new ResourceNotFoundException("Note not found");
        }
        staffNoteRepository.delete(note);
    }
}
