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

@Service
public class BookmarkFolderService {

    private final BookmarkFolderRepository bookmarkFolderRepository;
    private final BookmarkRepository bookmarkRepository;

    @Autowired
    public BookmarkFolderService(BookmarkFolderRepository bookmarkFolderRepository,
                                  BookmarkRepository bookmarkRepository) {
        this.bookmarkFolderRepository = bookmarkFolderRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

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

    public List<BookmarkFolder> findByStudentId(Long studentId) {
        return bookmarkFolderRepository.findByStudentId(studentId);
    }

    // Builds the { id, name, colour, bookmarkCount } shape the frontend
    // needs, using ONE grouped query for every folder's count rather than a
    // countByStudentIdAndFolderId call per folder (N+1).
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

    public BookmarkFolderResponse toResponse(BookmarkFolder folder) {
        long bookmarkCount = bookmarkRepository.countByStudentIdAndFolderId(folder.getStudentId(), folder.getId());
        return BookmarkFolderResponse.from(folder, bookmarkCount);
    }

    public BookmarkFolder findByIdAndStudentId(Long id, Long studentId) {
        BookmarkFolder folder = bookmarkFolderRepository.findById(id)

                .orElseThrow(() -> new ResourceNotFoundException("Bookmark folder not found"));

        if (!folder.getStudentId().equals(studentId)) {
            throw new ResourceNotFoundException("Bookmark folder not found");
        }
        return folder;
    }

    // Update (rename folder / change its colour)
    public BookmarkFolder updateFolder(Long id, Long studentId, String newName, String colour) {
        BookmarkFolder folder = findByIdAndStudentId(id, studentId);
        String cleanName = validateName(newName);

        // CHANGED: the previous check compared names with equals() and then
        // searched by name. Renaming "it issues" to "IT Issues" made equals()
        // false, so the search ran and found the folder being renamed — the
        // student was told their own folder was a duplicate and could not fix
        // their own capitalisation. Excluding by id resolves that.
        if (bookmarkFolderRepository
                .existsByStudentIdAndNameIgnoreCaseAndIdNot(studentId, cleanName, id)) {
            throw new DuplicateResourceException("A folder with this name already exists");
        }

        folder.setName(cleanName);

        // A null colour means the client omitted the field, not that they
        // want the colour cleared. Renaming a folder used to null its colour,
        // because the rename dialog only sends a name — the same class of
        // problem as mass assignment, where request data reaches state the
        // user never asked to change. If clearing a colour is ever needed it
        // should arrive as an explicit empty string, not an absent field.
        if (colour != null) {
            folder.setColour(colour);
        }
        return saveOrTranslateDuplicate(folder);
    }

    // CHANGED: added @Transactional. This performs several writes — unassign
    // every bookmark in the folder, then delete the folder. Without a
    // transaction, a failure part-way through leaves some bookmarks unfiled
    // and the folder still present.
    @Transactional
    public void deleteFolder(Long id, Long studentId) {
        BookmarkFolder folder = findByIdAndStudentId(id, studentId);

        // Bookmarks survive; only the grouping is removed. This is what the
        // confirmation dialog on the bookmarks page promises the student.
        // Inside a transaction these entities are managed, so JPA dirty
        // checking flushes the change — the explicit save() per row that was
        // here before was redundant.
        bookmarkRepository.findByStudentIdAndFolderId(studentId, id)
                .forEach(bookmark -> bookmark.setFolderId(null));

        bookmarkFolderRepository.delete(folder);
    }

    // The entity's @NotBlank and @Size(max = 60) only fire when Bean
    // Validation runs over the whole object. A raw String parameter bypasses
    // that entirely, so the same rules are enforced here.
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

    // existsBy... followed by save() is check-then-act: two concurrent
    // requests can both pass the check before either one saves. The unique
    // constraint on (student_id, name) is the real guarantee; this catches
    // the resulting database error and turns it into the same 409 the
    // pre-check would have produced. The pre-check is kept because it gives
    // the common case a clean message without relying on an exception.
    private BookmarkFolder saveOrTranslateDuplicate(BookmarkFolder folder) {
        try {
            return bookmarkFolderRepository.save(folder);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("A folder with this name already exists");
        }
    }
}
