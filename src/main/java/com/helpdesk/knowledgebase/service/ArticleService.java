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
 * Business rules for authoring, publishing and browsing articles. Search has
 * its own service (ArticleSearchService) - the multi-keyword intersection
 * logic is a distinct enough concern to keep separate from create/edit/
 * publish, matching the split BookmarkService/BookmarkFolderService already
 * uses in this project for a related pair of concerns.
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

    /**
     * Edits title/body/tags/categories. Deliberately not author-restricted -
     * OFFICER is one shared role in this project (SecurityConfig gates by
     * role, not by individual account), so any officer editing any article is
     * consistent with how every other hasRole("OFFICER") endpoint here
     * behaves. Author of record does not change on edit.
     */
    @Transactional
    public ArticleDetailResponse update(Long id, ArticleRequest request) {
        Article article = requireArticle(id);
        article.setTitle(request.title());
        article.setBody(request.body());
        article.setTags(normaliseTags(request.tags()));
        article.setCategories(resolveCategories(request.categoryIds()));
        // No explicit save() - the method is @Transactional, so Hibernate's
        // dirty checking flushes these field changes at commit. Same pattern
        // as ticketportal's deleteFolder relying on dirty checking rather
        // than an explicit save call.
        return ArticleDetailResponse.from(article);
    }

    /**
     * @param callerIsStudent when true, a DRAFT or ARCHIVED article is
     *                         reported as 404 rather than returned - a
     *                         student has no legitimate reason to see an
     *                         unpublished article, and 404 (rather than 403)
     *                         avoids confirming that an id belongs to a real,
     *                         just-not-visible-to-you article.
     */
    @Transactional(readOnly = true)
    public ArticleDetailResponse getById(Long id, boolean callerIsStudent) {
        Article article = requireArticle(id);
        if (callerIsStudent && article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Article not found: " + id);
        }
        return ArticleDetailResponse.from(article);
    }

    /**
     * All statuses, any officer. Hydrated with the same fetch-join query the
     * search path uses, so this list doesn't reintroduce the N+1 that query
     * exists to avoid.
     */
    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> listForManagement(Pageable pageable) {
        Page<Article> page = articleRepository.findAllByOrderByUpdatedAtDesc(pageable);
        var ids = page.getContent().stream().map(Article::getId).collect(Collectors.toList());
        var hydrated = articleRepository.findWithCategoriesAndTagsByIdIn(ids);
        // findWithCategoriesAndTagsByIdIn doesn't preserve the page's order
        // (IN clauses make no ordering guarantee), so look each hydrated
        // article back up by id rather than trusting the query's row order -
        // page.map() below walks the original, already-correctly-ordered page.
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

    @Transactional
    public void archive(Long id) {
        Article article = requireArticle(id);
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new ValidationException(
                    "Only a PUBLISHED article can be archived; this one is " + article.getStatus());
        }
        article.setStatus(ArticleStatus.ARCHIVED);
    }

    /**
     * Links {@code id} -> {@code relatedArticleId}. One-directional by
     * design - see the comment on Article.relatedArticles for why linking
     * A -> B is not the same as also linking B -> A.
     */
    @Transactional
    public void addRelated(Long id, Long relatedArticleId) {
        if (id.equals(relatedArticleId)) {
            // A self-reference can't be stopped by the join table's own
            // constraints (there's no "not equal to my own id" check JPA can
            // generate across two columns of the same row), so it has to be
            // guarded here. Left unguarded, it renders as an infinite
            // "see also" loop in the UI.
            throw new ValidationException("An article cannot be related to itself.");
        }
        Article article = requireArticle(id);
        Article related = requireArticle(relatedArticleId);
        article.getRelatedArticles().add(related);
    }

    /**
     * Unlinks {@code id} -> {@code relatedArticleId}. Only removes that one
     * direction, matching addRelated only ever adding one direction -
     * linking without a way to unlink would make a mistaken or outdated link
     * permanent, which a "see also" list shouldn't be.
     */
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
                // requireCategory throws ResourceNotFoundException (-> 404
                // via GlobalExceptionHandler) for an unknown id, rather than
                // silently dropping it - an officer who mistypes a category
                // id should see an error, not a quietly uncategorised article.
                .map(referenceDataService::requireCategory)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<String> normaliseTags(Set<String> tags) {
        // trim + toLowerCase(Locale.ROOT), same as BookmarkFolder.syncNameKey()
        // elsewhere in this project. Locale.ROOT is explicit and not
        // incidental: on a Turkish-locale JVM, "I".toLowerCase() produces a
        // dotless i (ı), and tags typed in English would silently stop
        // matching each other and stop matching search terms.
        return tags.stream()
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
