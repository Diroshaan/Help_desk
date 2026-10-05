package com.helpdesk.ticketportal.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.service.StudentService;
import com.helpdesk.ticketportal.dto.BookmarkFolderRequest;
import com.helpdesk.ticketportal.dto.BookmarkFolderResponse;
import com.helpdesk.ticketportal.entity.BookmarkFolder;
import com.helpdesk.ticketportal.service.BookmarkFolderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Student endpoints for bookmark folders. */
@RestController
@RequestMapping("/api/bookmark-folders")
public class BookmarkFolderController {

    private final BookmarkFolderService bookmarkFolderService;
    private final StudentService studentService;

    @Autowired
    public BookmarkFolderController(BookmarkFolderService bookmarkFolderService, StudentService studentService) {
        this.bookmarkFolderService = bookmarkFolderService;
        this.studentService = studentService;
    }

    @PostMapping
    public ResponseEntity<BookmarkFolderResponse> create(@Valid @RequestBody BookmarkFolderRequest request,
                                                           Authentication authentication) {
        BookmarkFolder folder = bookmarkFolderService.createFolder(
                currentStudentId(authentication), request.getName(), request.getColour());
        return ResponseEntity.status(HttpStatus.CREATED).body(bookmarkFolderService.toResponse(folder));
    }

    @GetMapping
    public List<BookmarkFolderResponse> findAll(Authentication authentication) {
        return bookmarkFolderService.findResponsesByStudentId(currentStudentId(authentication));
    }

    // PATCH, not PUT: fields left out of the request are kept, not cleared
    @PatchMapping("/{id}")
    public ResponseEntity<BookmarkFolderResponse> update(@PathVariable Long id,
                                                          @Valid @RequestBody BookmarkFolderRequest request,
                                                          Authentication authentication) {
        BookmarkFolder folder = bookmarkFolderService.updateFolder(
                id, currentStudentId(authentication), request.getName(), request.getColour());
        return ResponseEntity.ok(bookmarkFolderService.toResponse(folder));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        bookmarkFolderService.deleteFolder(id, currentStudentId(authentication));
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
