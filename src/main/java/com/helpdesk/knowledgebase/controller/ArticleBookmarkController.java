package com.helpdesk.knowledgebase.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.knowledgebase.dto.ArticleSummaryResponse;
import com.helpdesk.knowledgebase.service.ArticleBookmarkService;
import com.helpdesk.profile.entity.Student;
import com.helpdesk.profile.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A student's saved articles. studentId is derived from the session on every
 * call, never accepted as a path/query/body parameter - trusting a request
 * body for identity is exactly the IDOR class of bug this project's other
 * bookmarking code (BookmarkFolderController.currentStudentId()) already
 * guards against, and this copies that pattern rather than re-deciding it.
 */
@RestController
public class ArticleBookmarkController {

    private final ArticleBookmarkService bookmarkService;
    private final StudentRepository studentRepository;

    public ArticleBookmarkController(ArticleBookmarkService bookmarkService,
                                      StudentRepository studentRepository) {
        this.bookmarkService = bookmarkService;
        this.studentRepository = studentRepository;
    }

    @PostMapping("/api/articles/{id}/bookmark")
    @ResponseStatus(HttpStatus.CREATED)
    public void bookmark(@PathVariable Long id, Authentication authentication) {
        bookmarkService.bookmark(id, currentStudentId(authentication));
    }

    @DeleteMapping("/api/articles/{id}/bookmark")
    public ResponseEntity<Void> unbookmark(@PathVariable Long id, Authentication authentication) {
        bookmarkService.unbookmark(id, currentStudentId(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/articles/bookmarked")
    public List<ArticleSummaryResponse> listBookmarked(Authentication authentication) {
        return bookmarkService.listBookmarked(currentStudentId(authentication));
    }

    /**
     * Copy of BookmarkFolderController.currentStudentId(): the session
     * principal name is the student's email (StudentUserDetailsService
     * always sets it that way, even when the student logged in with their
     * student ID), so the student row is looked up by email, not by
     * re-parsing whatever the caller typed at login.
     */
    private Long currentStudentId(Authentication authentication) {
        Student student = studentRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No student account for " + authentication.getName()));
        return student.getId();
    }
}
