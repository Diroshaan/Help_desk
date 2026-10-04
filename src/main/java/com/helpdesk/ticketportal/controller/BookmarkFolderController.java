package com.helpdesk.ticketportal.controller;

import com.helpdesk.ticketportal.dto.BookmarkFolderRequest;
import com.helpdesk.ticketportal.dto.BookmarkFolderResponse;
import com.helpdesk.ticketportal.entity.BookmarkFolder;
import com.helpdesk.ticketportal.service.BookmarkFolderService;
import com.helpdesk.ticketportal.support.CurrentStudentResolver;
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
    private final CurrentStudentResolver currentStudent;

    @Autowired
    public BookmarkFolderController(BookmarkFolderService bookmarkFolderService, CurrentStudentResolver currentStudent) {
        this.bookmarkFolderService = bookmarkFolderService;
        this.currentStudent = currentStudent;
    }

    @PostMapping
    public ResponseEntity<BookmarkFolderResponse> create(@Valid @RequestBody BookmarkFolderRequest request,
                                                           Authentication authentication) {
        BookmarkFolder folder = bookmarkFolderService.createFolder(
                currentStudent.currentStudentId(authentication), request.getName(), request.getColour());
        return ResponseEntity.status(HttpStatus.CREATED).body(bookmarkFolderService.toResponse(folder));
    }

    @GetMapping
    public List<BookmarkFolderResponse> findAll(Authentication authentication) {
        return bookmarkFolderService.findResponsesByStudentId(currentStudent.currentStudentId(authentication));
    }

    // PATCH, not PUT: fields left out of the request are kept, not cleared
    @PatchMapping("/{id}")
    public ResponseEntity<BookmarkFolderResponse> update(@PathVariable Long id,
                                                          @Valid @RequestBody BookmarkFolderRequest request,
                                                          Authentication authentication) {
        BookmarkFolder folder = bookmarkFolderService.updateFolder(
                id, currentStudent.currentStudentId(authentication), request.getName(), request.getColour());
        return ResponseEntity.ok(bookmarkFolderService.toResponse(folder));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        bookmarkFolderService.deleteFolder(id, currentStudent.currentStudentId(authentication));
        return ResponseEntity.noContent().build();
    }
}
