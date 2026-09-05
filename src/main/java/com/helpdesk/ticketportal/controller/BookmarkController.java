package com.helpdesk.ticketportal.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.ticketportal.dto.BookmarkMoveRequest;
import com.helpdesk.ticketportal.dto.BookmarkRequest;
import com.helpdesk.ticketportal.dto.BookmarkResponse;
import com.helpdesk.ticketportal.entity.Bookmark;
import com.helpdesk.ticketportal.service.BookmarkService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final StudentService studentService;

    @Autowired
    public BookmarkController(BookmarkService bookmarkService, StudentService studentService) {
        this.bookmarkService = bookmarkService;
        this.studentService = studentService;
    }

    @PostMapping
    public ResponseEntity<BookmarkResponse> create(@Valid @RequestBody BookmarkRequest request,
                                                     Authentication authentication) {
        Bookmark bookmark = bookmarkService.createBookmark(
                currentStudentId(authentication), request.getTicketId(), request.getFolderId());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookmarkResponse.from(bookmark));
    }

    // With no folderId, returns every bookmark the student has; with one,
    // returns only the bookmarks currently filed in that folder.
    @GetMapping
    public List<BookmarkResponse> findAll(@RequestParam(required = false) Long folderId,
                                           Authentication authentication) {
        Long studentId = currentStudentId(authentication);
        List<Bookmark> bookmarks = (folderId != null)
                ? bookmarkService.findByStudentIdAndFolderId(studentId, folderId)
                : bookmarkService.findByStudentId(studentId);
        return bookmarks.stream().map(BookmarkResponse::from).toList();
    }

    // PATCH rather than PUT, same reasoning as BookmarkFolderController -
    // this only ever changes which folder a bookmark is filed under.
    @PatchMapping("/{id}/folder")
    public ResponseEntity<BookmarkResponse> moveToFolder(@PathVariable Long id,
                                                           @RequestBody BookmarkMoveRequest request,
                                                           Authentication authentication) {
        Bookmark bookmark = bookmarkService.moveToFolder(
                id, currentStudentId(authentication), request.getFolderId());
        return ResponseEntity.ok(BookmarkResponse.from(bookmark));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        bookmarkService.deleteBookmark(id, currentStudentId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long currentStudentId(Authentication authentication) {
        return studentService.findByEmail(authentication.getName())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in student not found"));
    }
}
