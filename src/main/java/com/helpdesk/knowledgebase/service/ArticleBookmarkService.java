package com.helpdesk.knowledgebase.service;

import com.helpdesk.common.exception.DuplicateResourceException;
import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.knowledgebase.dto.ArticleSummaryResponse;
import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleBookmark;
import com.helpdesk.knowledgebase.entity.ArticleStatus;
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
        // F5-N2: existsById only checked the article was there, not that it
        // was PUBLISHED - so a student who learned a draft's id (from a
        // related-articles list, say, or just by guessing sequential ids)
        // could bookmark and then read a draft through
        // GET /api/articles/bookmarked, bypassing getById's own 404. Loading
        // the article and checking status here closes that: a draft or
        // archived article is reported 404, the same code a nonexistent id
        // gets, so a student can't tell "doesn't exist" from "exists but you
        // can't see it" - same reasoning as ArticleService.getById.
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + articleId));
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
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
     *
     * F5-N2: only PUBLISHED articles are returned, even though the
     * underlying bookmark row is never deleted for a non-published one (see
     * bookmark() above - archiving doesn't delete the row, so this method,
     * not the data, is what hides it). If the article is republished later,
     * the student's save simply reappears here with no re-save needed -
     * that's the point of filtering at read time instead of deleting the
     * bookmark when the article is archived.
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
                .filter(a -> a != null && a.getStatus() == ArticleStatus.PUBLISHED)
                .map(ArticleSummaryResponse::from)
                .collect(Collectors.toList());
    }
}
