package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticketportal.entity.Bookmark;
import com.helpdesk.ticketportal.repository.BookmarkFolderRepository;
import com.helpdesk.ticketportal.repository.BookmarkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final BookmarkFolderRepository bookmarkFolderRepository;
    private final TicketRepository ticketRepository;

    @Autowired
    public BookmarkService(BookmarkRepository bookmarkRepository,
                            BookmarkFolderRepository bookmarkFolderRepository,
                            TicketRepository ticketRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.bookmarkFolderRepository = bookmarkFolderRepository;
        this.ticketRepository = ticketRepository;
    }

    public Bookmark createBookmark(Long studentId, Long ticketId, Long folderId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket not found");
        }
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

        return bookmarkRepository.save(bookmark);
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
    public Bookmark moveToFolder(Long id, Long studentId, Long folderId) {
        Bookmark bookmark = findByIdAndStudentId(id, studentId);
        if (folderId != null) {
            requireOwnedFolder(folderId, studentId);
        }
        bookmark.setFolderId(folderId);
        return bookmarkRepository.save(bookmark);
    }

    public void deleteBookmark(Long id, Long studentId) {
        Bookmark bookmark = findByIdAndStudentId(id, studentId);
        bookmarkRepository.delete(bookmark);
    }

    //findById alone would let one student file a bookmark into someone
    //else's folder. This is the same ownership check BookmarkFolderService
    private void requireOwnedFolder(Long folderId, Long studentId) {
        boolean ownsFolder = bookmarkFolderRepository.findById(folderId)
                .map(folder -> folder.getStudentId().equals(studentId))
                .orElse(false);
        if (!ownsFolder) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
    }
}
