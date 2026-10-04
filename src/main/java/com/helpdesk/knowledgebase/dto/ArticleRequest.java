package com.helpdesk.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Body for creating (as DRAFT) or editing an article.
 * No id, status or author fields, so clients can't set them. Status only changes
 * through publish/archive, and the author comes from the session.
 */
public record ArticleRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 10000)
        String body,

        // optional; trimmed and lower-cased in ArticleService
        Set<String> tags,

        // Every article needs at least one category. Max 5 is just a sanity limit;
        // unknown ids give a 404 in the service.
        @NotEmpty(message = "Choose at least one category")
        @Size(max = 5)
        Set<Long> categoryIds
) {
    public ArticleRequest {
        // null becomes an empty set, so @NotEmpty also catches a missing categoryIds
        if (tags == null) {
            tags = Set.of();
        }
        if (categoryIds == null) {
            categoryIds = Set.of();
        }
    }
}
