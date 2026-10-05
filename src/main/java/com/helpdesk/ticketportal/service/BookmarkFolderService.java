package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticketportal.dto.BookmarkFolderResponse;
import com.helpdesk.ticketportal.entity.Bookmark;
import com.helpdesk.ticketportal.entity.BookmarkFolder;
import com.helpdesk.ticketportal.repository.BookmarkFolderRepository;
import com.helpdesk.ticketportal.repository.BookmarkRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Folders a student uses to group their ticket bookmarks. */
@Service
public class BookmarkFolderService {

    private static final String DUPLICATE_NAME = "A folder with this name already exists";

    private final BookmarkFolderRepository bookmarkFolderRepository;
    private final BookmarkRepository bookmarkRepository;

    // Spring injects the folder and bookmark repositories
    @Autowired
    public BookmarkFolderService(BookmarkFolderRepository bookmarkFolderRepository,
                                 BookmarkRepository bookmarkRepository) {
        this.bookmarkFolderRepository = bookmarkFolderRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    // Creates a folder after checking the name is valid and not already used
    public BookmarkFolder createFolder(Long studentId, String name, String colour) {
        String cleanName = validateName(name);
        if (bookmarkFolderRepository.existsByStudentIdAndNameIgnoreCase(studentId, cleanName)) {
            throw new DuplicateResourceException(DUPLICATE_NAME);
        }

        BookmarkFolder folder = new BookmarkFolder();
        folder.setStudentId(studentId);
        folder.setName(cleanName);
        folder.setColour(colour);
        return save(folder);
    }

    // Returns the student's folders, each with its bookmark count
    public List<BookmarkFolderResponse> findResponsesByStudentId(Long studentId) {
        List<BookmarkFolderResponse> responses = new ArrayList<>();
        for (BookmarkFolder folder : bookmarkFolderRepository.findByStudentId(studentId)) {
            responses.add(toResponse(folder));
        }
        return responses;
    }

    // Builds the response for one folder, including its bookmark count
    public BookmarkFolderResponse toResponse(BookmarkFolder folder) {
        long count = bookmarkRepository.countByStudentIdAndFolderId(folder.getStudentId(), folder.getId());
        return BookmarkFolderResponse.from(folder, count);
    }

    // Loads a folder; another student's folder is a 404, the same as a missing one
    public BookmarkFolder findByIdAndStudentId(Long id, Long studentId) {
        BookmarkFolder folder = bookmarkFolderRepository.findById(id).orElse(null);
        if (folder == null) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
        if (!folder.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
        return folder;
    }

    // Renames a folder, and changes its colour if a new one was sent
    public BookmarkFolder updateFolder(Long id, Long studentId, String newName, String colour) {
        BookmarkFolder folder = findByIdAndStudentId(id, studentId);
        String cleanName = validateName(newName);

        // ignores this folder, so changing only the capitalisation is allowed
        if (bookmarkFolderRepository.existsByStudentIdAndNameIgnoreCaseAndIdNot(studentId, cleanName, id)) {
            throw new DuplicateResourceException(DUPLICATE_NAME);
        }

        folder.setName(cleanName);
        if (colour != null) {
            folder.setColour(colour);
        }
        return save(folder);
    }

    // Deletes a folder and moves its bookmarks to "no folder", all in one transaction
    @Transactional
    public void deleteFolder(Long id, Long studentId) {
        BookmarkFolder folder = findByIdAndStudentId(id, studentId);
        for (Bookmark bookmark : bookmarkRepository.findByStudentIdAndFolderId(studentId, id)) {
            bookmark.setFolderId(null);
        }
        bookmarkFolderRepository.delete(folder);
    }

    // Checks the name is present and at most 60 characters, and removes extra spaces
    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Folder name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 60) {
            throw new ValidationException("Folder name must be 60 characters or fewer");
        }
        return trimmed;
    }

    // Saves the folder; if two requests create the same name at once, the database
    // blocks the second and we return the same "already exists" error
    private BookmarkFolder save(BookmarkFolder folder) {
        try {
            return bookmarkFolderRepository.save(folder);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(DUPLICATE_NAME);
        }
    }
}
