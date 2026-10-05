package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticketportal.entity.Bookmark;
import com.helpdesk.ticketportal.entity.BookmarkFolder;
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

    private static final String ALREADY_BOOKMARKED = "This ticket is already bookmarked";

    private final BookmarkRepository bookmarkRepository;
    private final BookmarkFolderRepository bookmarkFolderRepository;
    private final TicketService ticketService;

    // Spring injects the repositories and TicketService this service needs
    @Autowired
    public BookmarkService(BookmarkRepository bookmarkRepository,
                            BookmarkFolderRepository bookmarkFolderRepository,
                            TicketService ticketService) {
        this.bookmarkRepository = bookmarkRepository;
        this.bookmarkFolderRepository = bookmarkFolderRepository;
        this.ticketService = ticketService;
    }

    // Creates a new bookmarked ticket
    @Transactional
    public Bookmark createBookmark(Long studentId, Long ticketId, Long folderId) {
        // must be the student's own ticket
        ticketService.getOwnedTicket(ticketId, studentId);
        if (folderId != null) {
            requireOwnedFolder(folderId, studentId);
        }
        if (bookmarkRepository.existsByStudentIdAndTicketId(studentId, ticketId)) {
            throw new DuplicateResourceException(ALREADY_BOOKMARKED);
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
            throw new DuplicateResourceException(ALREADY_BOOKMARKED);
        }
    }

    // Returns all of the student's bookmarks
    public List<Bookmark> findByStudentId(Long studentId) {
        return bookmarkRepository.findByStudentId(studentId);
    }

    // Returns the student's bookmarks in one folder
    public List<Bookmark> findByStudentIdAndFolderId(Long studentId, Long folderId) {
        return bookmarkRepository.findByStudentIdAndFolderId(studentId, folderId);
    }

    // Loads a bookmark; 404 if it is missing or belongs to another student
    public Bookmark findByIdAndStudentId(Long id, Long studentId) {
        Bookmark bookmark = bookmarkRepository.findById(id).orElse(null);
        if (bookmark == null) {
            throw new ResourceNotFoundException("Bookmark not found");
        }

        if (!bookmark.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Bookmark not found");
        }
        return bookmark;
    }

    // Moves a bookmark into one of the student's folders (null unfiles it)
    @Transactional
    public Bookmark moveToFolder(Long id, Long studentId, Long folderId) {
        Bookmark bookmark = findByIdAndStudentId(id, studentId);
        if (folderId != null) {
            requireOwnedFolder(folderId, studentId);
        }
        bookmark.setFolderId(folderId);
        return bookmarkRepository.save(bookmark);
    }

    // Deletes one of the student's bookmarks
    @Transactional
    public void deleteBookmark(Long id, Long studentId) {
        Bookmark bookmark = findByIdAndStudentId(id, studentId);
        bookmarkRepository.delete(bookmark);
    }

    // another student's folder gives 404, same as a missing one
    private void requireOwnedFolder(Long folderId, Long studentId) {
        BookmarkFolder folder = bookmarkFolderRepository.findById(folderId).orElse(null);
        if (folder == null) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
        if (!folder.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
    }
}
