package com.helpdesk.ticketportal.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.ticketportal.dto.BookmarkFolderResponse;
import com.helpdesk.ticketportal.entity.BookmarkFolder;
import com.helpdesk.ticketportal.repository.BookmarkFolderRepository;
import com.helpdesk.ticketportal.repository.BookmarkRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Folders a student uses to group their ticket bookmarks. */
@Service
public class BookmarkFolderService {

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
            throw new DuplicateResourceException("A folder with this name already exists");
        }

        BookmarkFolder folder = new BookmarkFolder();
        folder.setStudentId(studentId);
        folder.setName(cleanName);
        folder.setColour(colour);

        return saveOrTranslateDuplicate(folder);
    }

    // Returns all of the student's folders
    public List<BookmarkFolder> findByStudentId(Long studentId) {
        return bookmarkFolderRepository.findByStudentId(studentId);
    }

    // Returns the student's folders with their bookmark counts (one count query)
    public List<BookmarkFolderResponse> findResponsesByStudentId(Long studentId) {
        List<BookmarkFolder> folders = bookmarkFolderRepository.findByStudentId(studentId);

        Map<Long, Long> countsByFolderId = bookmarkRepository.countByFolderIdForStudent(studentId).stream()
                .collect(Collectors.toMap(
                        BookmarkRepository.FolderBookmarkCount::getFolderId,
                        BookmarkRepository.FolderBookmarkCount::getCount));

        return folders.stream()
                .map(folder -> BookmarkFolderResponse.from(folder, countsByFolderId.getOrDefault(folder.getId(), 0L)))
                .collect(Collectors.toList());
    }

    // Builds the response for one folder, including its bookmark count
    public BookmarkFolderResponse toResponse(BookmarkFolder folder) {
        long bookmarkCount = bookmarkRepository.countByStudentIdAndFolderId(folder.getStudentId(), folder.getId());
        return BookmarkFolderResponse.from(folder, bookmarkCount);
    }

    // another student's folder is a 404, not a 403
    public BookmarkFolder findByIdAndStudentId(Long id, Long studentId) {
        BookmarkFolder folder = bookmarkFolderRepository.findById(id)

                .orElseThrow(() -> new ResourceNotFoundException("Bookmark folder not found"));

        if (!folder.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
        return folder;
    }

    // Renames or recolours a folder after checking the new name is not a duplicate
    public BookmarkFolder updateFolder(Long id, Long studentId, String newName, String colour) {
        BookmarkFolder folder = findByIdAndStudentId(id, studentId);
        String cleanName = validateName(newName);

        // excludes this folder, so changing only the capitalisation isn't a duplicate
        if (bookmarkFolderRepository
                .existsByStudentIdAndNameIgnoreCaseAndIdNot(studentId, cleanName, id)) {
            throw new DuplicateResourceException("A folder with this name already exists");
        }

        folder.setName(cleanName);

        // null means the field was left out (the rename dialog only sends a name), so keep the colour
        if (colour != null) {
            folder.setColour(colour);
        }
        return saveOrTranslateDuplicate(folder);
    }

    // One transaction, so a failure can't leave half the bookmarks unfiled.
    @Transactional
    public void deleteFolder(Long id, Long studentId) {
        BookmarkFolder folder = findByIdAndStudentId(id, studentId);

        // bookmarks are kept, just unfiled (dirty checking saves the change)
        bookmarkRepository.findByStudentIdAndFolderId(studentId, id)
                .forEach(bookmark -> bookmark.setFolderId(null));

        bookmarkFolderRepository.delete(folder);
    }

    // same rules as the entity, since a plain String parameter skips bean validation
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

    // Two requests can both pass the exists check; the unique (student_id, name)
    // constraint catches the second and we turn it into the same 409.
    private BookmarkFolder saveOrTranslateDuplicate(BookmarkFolder folder) {
        try {
            return bookmarkFolderRepository.save(folder);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("A folder with this name already exists");
        }
    }
}
