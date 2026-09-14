package com.helpdesk.knowledgebase.dto;

import com.helpdesk.knowledgebase.entity.Article;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outgoing shape for search results and the officer "manage" list.
 * Deliberately thin - no body, no relatedArticles - because this is what
 * ArticleDetailResponse embeds for relatedArticles, and a full Article-shaped
 * DTO inside another one is exactly the A -> B -> A recursion this class
 * exists to stop (F5_Knowledge_Base_Spec.md section 5).
 *
 * Includes authorName even though F5_Knowledge_Base_Spec.md section 5 lists
 * it only on the detail response - F5_Build_Guide.md section 2 (the more
 * recent, step-by-step companion doc) lists it here too, and showing who
 * wrote a guide in a search results list is a reasonable thing for a help
 * desk FAQ to show. Flagged as a documented choice where the two source
 * documents disagreed, not an oversight.
 *
 * Never built outside a transaction: article.getAuthor(), .getTags() and
 * .getCategories() are all LAZY, so from()/fromAll() must run inside the
 * @Transactional service method that loaded the Article, never after the
 * controller has already returned.
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
                // Depends on Officer.getFullName(), which Diroshaan is adding
                // in his own shared-file PR to common/user (not on develop
                // yet as of this branch) - see F5_Implementation_Notes.md
                // "Sequencing". This line doesn't compile until that lands.
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
