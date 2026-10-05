package com.helpdesk.knowledgebase.dto;

import com.helpdesk.knowledgebase.entity.Article;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Short article shape for search results, the manage list and related articles.
 * Has no body or related list, so related articles can't recurse A -> B -> A.
 * Build it inside the service transaction, since author, tags and categories are lazy.
 */
public record ArticleSummaryResponse(
        Long id,
        String title,
        String excerpt,
        Set<String> tags,
        List<String> categoryNames,
        String authorName,
        LocalDateTime updatedAt
) {

    private static final int EXCERPT_LENGTH = 200;

    public static ArticleSummaryResponse from(Article article) {
        String body = article.getBody();
        String excerpt = body.length() <= EXCERPT_LENGTH
                ? body
                : body.substring(0, EXCERPT_LENGTH) + "...";

        List<String> categoryNames = article.getCategories().stream()
                .map(c -> c.getName())
                .sorted()
                .collect(Collectors.toList());

        return new ArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                excerpt,
                article.getTags(),
                categoryNames,
                article.getAuthor().getFullName(),
                article.getUpdatedAt()
        );
    }

    public static List<ArticleSummaryResponse> fromAll(List<Article> articles) {
        return articles.stream()
                .map(ArticleSummaryResponse::from)
                .collect(Collectors.toList());
    }
}
