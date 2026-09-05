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

    // PATCH rather than PUT: PUT means "replace the whole resource", so
    // nulling fields the request omitted would be correct PUT behaviour.
    // This endpoint changes individual fields, so PATCH is the honest verb
    // and removes the ambiguity that caused the colour to be lost.
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
        return studentService.findByEmail(authentication.getName())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in student not found"));
    }
}
