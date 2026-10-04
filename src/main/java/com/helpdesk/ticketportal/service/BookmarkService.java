package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticketportal.entity.Bookmark;
import com.helpdesk.ticketportal.repository.BookmarkFolderRepository;
import com.helpdesk.ticketportal.repository.BookmarkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** A student's bookmarks on their own tickets, optionally filed into folders. */
@Service
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final BookmarkFolderRepository bookmarkFolderRepository;
    private final TicketService ticketService;

    @Autowired
    public BookmarkService(BookmarkRepository bookmarkRepository,
                            BookmarkFolderRepository bookmarkFolderRepository,
                            TicketService ticketService) {
        this.bookmarkRepository = bookmarkRepository;
        this.bookmarkFolderRepository = bookmarkFolderRepository;
        this.ticketService = ticketService;
    }

    @Transactional
    public Bookmark createBookmark(Long studentId, Long ticketId, Long folderId) {
        // must be the student's own ticket; someone else's is a 404 so ids can't be probed
        ticketService.getOwnedTicket(ticketId, studentId);
        if (folderId != null) {
            requireOwnedFolder(folderId, studentId);
        }
        if (bookmarkRepository.existsByStudentIdAndTicketId(studentId, ticketId)) {
            throw new DuplicateResourceException("This ticket is already bookmarked");
        }

        Bookmark bookmark = new Bookmark();
        bookmark.setStudentId(studentId);
        bookmark.setTicketId(ticketId);
        bookmark.setFolderId(folderId);

        // Two clicks at once can both pass the check above; the unique constraint
        // rejects the second insert and we return the same 409.
        try {
            return bookmarkRepository.saveAndFlush(bookmark);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("This ticket is already bookmarked");
        }
    }

    public List<Bookmark> findByStudentId(Long studentId) {
        return bookmarkRepository.findByStudentId(studentId);
    }

    public List<Bookmark> findByStudentIdAndFolderId(Long studentId, Long folderId) {
        return bookmarkRepository.findByStudentIdAndFolderId(studentId, folderId);
    }

    public Bookmark findByIdAndStudentId(Long id, Long studentId) {
        Bookmark bookmark = bookmarkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bookmark not found"));

        if (!bookmark.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Bookmark not found");
        }
        return bookmark;
    }

    @Transactional
    public Bookmark moveToFolder(Long id, Long studentId, Long folderId) {
        Bookmark bookmark = findByIdAndStudentId(id, studentId);
        if (folderId != null) {
            requireOwnedFolder(folderId, studentId);
        }
        bookmark.setFolderId(folderId);
        return bookmarkRepository.save(bookmark);
    }

    @Transactional
    public void deleteBookmark(Long id, Long studentId) {
        Bookmark bookmark = findByIdAndStudentId(id, studentId);
        bookmarkRepository.delete(bookmark);
    }

    // another student's folder gives 404, same as a missing one
    private void requireOwnedFolder(Long folderId, Long studentId) {
        boolean ownsFolder = bookmarkFolderRepository.findById(folderId)
                .map(folder -> folder.getStudentId().equals(studentId))
                .orElse(false);
        if (!ownsFolder) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
    }
}
