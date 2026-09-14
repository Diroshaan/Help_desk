package com.helpdesk.knowledgebase.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.knowledgebase.dto.ArticleSummaryResponse;
import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleBookmark;
import com.helpdesk.knowledgebase.repository.ArticleBookmarkRepository;
import com.helpdesk.knowledgebase.repository.ArticleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Requirement spec 3.2 #4 - a student saves an article, at most once.
 *
 * The uniqueness rule is enforced twice, deliberately, not redundantly:
 *   1. A Java existsBy... check first, so the common case (student hasn't
 *      already bookmarked this) gets a fast, friendly 409 without ever
 *      reaching the database's constraint machinery.
 *   2. The database's uq_article_bookmark_student_article unique constraint
 *      as the real defence - two near-simultaneous clicks can both pass step
 *      1 before either has inserted, and only the constraint (via
 *      DataIntegrityViolationException, caught below) can't be raced.
 * ticketportal's BookmarkService does only step 1, with no constraint behind
 * it - see the note on ArticleBookmark for why that's a real bug there.
 */
@Service
public class ArticleBookmarkService {

    private final ArticleBookmarkRepository bookmarkRepository;
    private final ArticleRepository articleRepository;

    public ArticleBookmarkService(ArticleBookmarkRepository bookmarkRepository,
                                   ArticleRepository articleRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.articleRepository = articleRepository;
    }

    @Transactional
    public void bookmark(Long articleId, Long studentId) {
        if (!articleRepository.existsById(articleId)) {
            throw new ResourceNotFoundException("Article not found: " + articleId);
        }
        if (bookmarkRepository.existsByStudentIdAndArticleId(studentId, articleId)) {
            throw new DuplicateResourceException("Article already bookmarked.");
        }
        try {
            bookmarkRepository.save(new ArticleBookmark(articleId, studentId));
        } catch (DataIntegrityViolationException e) {
            // The race the existsBy... check above can't close: two requests
            // both saw "not bookmarked yet" and both reached save(). The
            // unique constraint rejects the second insert at the database;
            // this turns that into the same 409 the check-then-act path
            // would have produced if it had won the race.
            throw new DuplicateResourceException("Article already bookmarked.");
        }
    }

    @Transactional
    public void unbookmark(Long articleId, Long studentId) {
        ArticleBookmark bookmark = bookmarkRepository
                .findByStudentIdAndArticleId(studentId, articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Bookmark not found."));
        bookmarkRepository.delete(bookmark);
    }

    /**
     * Caller's own saved articles only - studentId always comes from the
     * session in ArticleBookmarkController, never from a path or query
     * parameter, so there is no way to pass another student's id in here.
     */
    @Transactional(readOnly = true)
    public List<ArticleSummaryResponse> listBookmarked(Long studentId) {
        List<Long> articleIds = bookmarkRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(ArticleBookmark::getArticleId)
                .collect(Collectors.toList());
        if (articleIds.isEmpty()) {
            return List.of();
        }
        List<Article> articles = articleRepository.findWithCategoriesAndTagsByIdIn(articleIds);
        // findWithCategoriesAndTagsByIdIn doesn't preserve order; re-apply
        // the bookmark list's own (most-recently-saved-first) order.
        var byId = articles.stream().collect(Collectors.toMap(Article::getId, a -> a));
        return articleIds.stream()
                .map(byId::get)
                .filter(a -> a != null)
                .map(ArticleSummaryResponse::from)
                .collect(Collectors.toList());
    }
}
