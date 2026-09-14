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
     * Builds the full detail view, including relatedArticles as summaries.
     * Must run inside the same transaction that loaded {@code article} - see
     * the same warning on ArticleSummaryResponse.from().
     */
    public static ArticleDetailResponse from(Article article) {
        List<String> categoryNames = article.getCategories().stream()
                .map(c -> c.getName())
                .sorted()
                .collect(Collectors.toList());

        List<ArticleSummaryResponse> related = article.getRelatedArticles().stream()
                .map(ArticleSummaryResponse::from)
                .collect(Collectors.toList());

        return new ArticleDetailResponse(
                article.getId(),
                article.getTitle(),
                article.getBody(),
                article.getStatus(),
                article.getTags(),
                categoryNames,
                // See the same Officer.getFullName() note in
                // ArticleSummaryResponse.from() - depends on Diroshaan's
                // shared-file PR, not yet on develop.
                article.getAuthor().getFullName(),
                related,
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
