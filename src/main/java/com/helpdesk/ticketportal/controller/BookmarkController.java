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

import java.util.ArrayList;
import java.util.List;

/** Student endpoints for ticket bookmarks. Only the student's own bookmarks are reachable. */
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

    @GetMapping
    public List<BookmarkResponse> findAll(@RequestParam(required = false) Long folderId,
                                           Authentication authentication) {
        Long studentId = currentStudentId(authentication);
        List<Bookmark> bookmarks;
        if (folderId != null) {
            bookmarks = bookmarkService.findByStudentIdAndFolderId(studentId, folderId);
        } else {
            bookmarks = bookmarkService.findByStudentId(studentId);
        }

        List<BookmarkResponse> responses = new ArrayList<>();
        for (Bookmark bookmark : bookmarks) {
            responses.add(BookmarkResponse.from(bookmark));
        }
        return responses;
    }

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
        Student student = studentService.findByEmail(authentication.getName()).orElse(null);
        if (student == null) {
            throw new ResourceNotFoundException("Logged-in student not found");
        }
        return student.getId();
    }
}
