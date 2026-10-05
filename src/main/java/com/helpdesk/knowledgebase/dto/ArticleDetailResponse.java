package com.helpdesk.knowledgebase.dto;

import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Full article for GET /api/articles/{id}. Related articles use the summary shape
 * to avoid infinite recursion. status is there for the officer manage view.
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

    // officer view: related articles of any status
    public static ArticleDetailResponse from(Article article) {
        return from(article, false);
    }

    /**
     * With publishedRelatedOnly, students only see PUBLISHED related articles, so a
     * linked draft or archived article's title doesn't leak. Filtered on read, since
     * statuses change after linking. Call inside the loading transaction.
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
