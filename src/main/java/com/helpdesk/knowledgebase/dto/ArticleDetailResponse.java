package com.helpdesk.knowledgebase.dto;

import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outgoing shape for GET /api/articles/{id}.
 *
 * relatedArticles is List<ArticleSummaryResponse>, not List<Article> or
 * List<ArticleDetailResponse> - summary, not detail, which is what breaks
 * the A -> B -> A infinite recursion a self-referencing relationship would
 * otherwise cause Jackson to attempt.
 *
 * status is included even though it isn't in F5_Knowledge_Base_Spec.md
 * section 5's field list - the officer "manage" view (GET /api/articles/manage)
 * needs to show DRAFT/ARCHIVED articles too, and there is no other field on
 * this response that tells the caller which one they're looking at.
 */
public record ArticleDetailResponse(
        Long id,
        String title,
        String body,
        ArticleStatus status,
        Set<String> tags,
        List<String> categoryNames,
        String authorName,
        List<ArticleSummaryResponse> relatedArticles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * "Show all" - every related article regardless of status, for the
     * officer/admin view where managing the links (deciding whether a draft
     * is worth pointing at yet) is exactly the point.
     */
    public static ArticleDetailResponse from(Article article) {
        return from(article, false);
    }

    /**
     * F5-N1 fix: a related article's own status was never checked here,
     * only the top-level article's - so a published article linked to a
     * draft (or one later archived) leaked the draft's title through this
     * list, even though GET /api/articles/{draftId} itself correctly 404s
     * for a student. {@code publishedRelatedOnly} filters relatedArticles to
     * PUBLISHED before mapping, when true.
     *
     * Filtered at *read* time, not at link time - addRelated still allows
     * linking any status, deliberately. An article PUBLISHED today can be
     * ARCHIVED tomorrow, and a link made while both sides were published
     * shouldn't need every existing link revisited the moment one side's
     * status changes; checking status on every read is the simpler rule
     * that stays correct automatically as statuses move.
     *
     * Must run inside the same transaction that loaded {@code article} - see
     * the same warning on ArticleSummaryResponse.from().
     */
    public static ArticleDetailResponse from(Article article, boolean publishedRelatedOnly) {
        List<String> categoryNames = article.getCategories().stream()
                .map(c -> c.getName())
                .sorted()
                .collect(Collectors.toList());

        List<ArticleSummaryResponse> related = article.getRelatedArticles().stream()
                .filter(r -> !publishedRelatedOnly || r.getStatus() == ArticleStatus.PUBLISHED)
                .map(ArticleSummaryResponse::from)
                .collect(Collectors.toList());

        return new ArticleDetailResponse(
                article.getId(),
                article.getTitle(),
                article.getBody(),
                article.getStatus(),
                article.getTags(),
                categoryNames,
                article.getAuthor().getFullName(),
                related,
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
