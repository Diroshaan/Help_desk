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
        // Ownership, not mere existence: 404 for a missing ticket AND for someone else's,
        // so the response never reveals which ticket ids exist (no enumeration).
        // existsById alone let student A bookmark student B's ticket (IDOR, F3-N1).
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

        // Same pattern as F5's ArticleBookmarkService.bookmark: the existsBy...
        // check above gives the common case a clean message, but two clicks
        // at once can both pass it. The uq_bookmark_student_ticket constraint
        // rejects the second insert; saveAndFlush makes that happen inside
        // this try, and it becomes the same 409 the check would have given.
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

    //Moves a bookmark into a different folder, or unfiles it (folderId == null).
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

    //findById alone would let one student file a bookmark into someone
    //else's folder. This is the same ownership check BookmarkFolderService
    //applies in findByIdAndStudentId, answering 404 for another student's folder.
    private void requireOwnedFolder(Long folderId, Long studentId) {
        boolean ownsFolder = bookmarkFolderRepository.findById(folderId)
                .map(folder -> folder.getStudentId().equals(studentId))
                .orElse(false);
        if (!ownsFolder) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
    }
}
