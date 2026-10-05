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

/** Lets a student save (bookmark) published articles, at most once each. */
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
        // Drafts and archived articles give 404, same as a missing id, so students
        // can't bookmark (and then read) them.
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
            // two clicks can both pass the check above; the unique constraint stops the second
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
     * The student's saved articles, newest first. Only PUBLISHED ones are shown; the
     * bookmark row is kept, so it comes back if the article is published again.
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
        // the IN query loses the order, so re-apply the bookmark order
        var byId = articles.stream().collect(Collectors.toMap(Article::getId, a -> a));
        return articleIds.stream()
                .map(byId::get)
                .filter(a -> a != null && a.getStatus() == ArticleStatus.PUBLISHED)
                .map(ArticleSummaryResponse::from)
                .collect(Collectors.toList());
    }
}
