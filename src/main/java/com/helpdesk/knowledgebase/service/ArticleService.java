package com.helpdesk.knowledgebase.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.reference.entity.Category;
import com.helpdesk.common.reference.service.ReferenceDataService;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.knowledgebase.dto.ArticleDetailResponse;
import com.helpdesk.knowledgebase.dto.ArticleRequest;
import com.helpdesk.knowledgebase.dto.ArticleSummaryResponse;
import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleStatus;
import com.helpdesk.knowledgebase.repository.ArticleRepository;
import jakarta.validation.ValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business rules for writing, publishing and reading articles. Search is in
 * ArticleSearchService.
 */
@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ReferenceDataService referenceDataService;

    public ArticleService(ArticleRepository articleRepository,
                           ReferenceDataService referenceDataService) {
        this.articleRepository = articleRepository;
        this.referenceDataService = referenceDataService;
    }

    @Transactional
    public ArticleDetailResponse create(ArticleRequest request, Officer author) {
        Article article = new Article(request.title(), request.body(), author);
        article.setTags(normaliseTags(request.tags()));
        article.setCategories(resolveCategories(request.categoryIds()));
        Article saved = articleRepository.save(article);
        return ArticleDetailResponse.from(saved);
    }

    // Any officer can edit any article; the author stays the same.
    @Transactional
    public ArticleDetailResponse update(Long id, ArticleRequest request) {
        Article article = requireArticle(id);
        article.setTitle(request.title());
        article.setBody(request.body());
        article.setTags(normaliseTags(request.tags()));
        article.setCategories(resolveCategories(request.categoryIds()));
        // no save() needed, dirty checking writes the changes on commit
        return ArticleDetailResponse.from(article);
    }

    /**
     * Students only see PUBLISHED articles: drafts and archived ones give 404, and
     * their related list is filtered to PUBLISHED too.
     */
    @Transactional(readOnly = true)
    public ArticleDetailResponse getById(Long id, boolean callerIsStudent) {
        Article article = requireArticle(id);
        if (callerIsStudent && article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Article not found: " + id);
        }
        return ArticleDetailResponse.from(article, callerIsStudent);
    }

    // All statuses, for officers. Uses the fetch-join query to avoid N+1.
    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> listForManagement(Pageable pageable) {
        Page<Article> page = articleRepository.findAllByOrderByUpdatedAtDesc(pageable);
        var ids = page.getContent().stream().map(Article::getId).collect(Collectors.toList());
        var hydrated = articleRepository.findWithCategoriesAndTagsByIdIn(ids);
        // the IN query doesn't keep the page order, so map back by id
        var byId = hydrated.stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
        return page.map(a -> ArticleSummaryResponse.from(byId.get(a.getId())));
    }

    @Transactional
    public void publish(Long id) {
        Article article = requireArticle(id);
        if (article.getStatus() != ArticleStatus.DRAFT) {
            throw new ValidationException(
                    "Only a DRAFT article can be published; this one is " + article.getStatus());
        }
        article.setStatus(ArticleStatus.PUBLISHED);
    }

    // only PUBLISHED articles can be archived
    @Transactional
    public void archive(Long id) {
        Article article = requireArticle(id);
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new ValidationException(
                    "Only a PUBLISHED article can be archived; this one is " + article.getStatus());
        }
        article.setStatus(ArticleStatus.ARCHIVED);
    }

    // links one way only: A -> B doesn't add B -> A
    @Transactional
    public void addRelated(Long id, Long relatedArticleId) {
        if (id.equals(relatedArticleId)) {
            // the database can't block this, so we check it here
            throw new ValidationException("An article cannot be related to itself.");
        }
        Article article = requireArticle(id);
        Article related = requireArticle(relatedArticleId);
        article.getRelatedArticles().add(related);
    }

    // removes only the A -> B direction
    @Transactional
    public void removeRelated(Long id, Long relatedArticleId) {
        Article article = requireArticle(id);
        Article related = requireArticle(relatedArticleId);
        if (!article.getRelatedArticles().remove(related)) {
            throw new ResourceNotFoundException(
                    "Article " + id + " is not related to " + relatedArticleId);
        }
    }

    private Article requireArticle(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + id));
    }

    private Set<Category> resolveCategories(Set<Long> categoryIds) {
        return categoryIds.stream()
                // an unknown id is a 404, not silently dropped
                .map(referenceDataService::requireCategory)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<String> normaliseTags(Set<String> tags) {
        // Locale.ROOT so a Turkish-locale server doesn't turn "I" into a dotless i
        return tags.stream()
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
