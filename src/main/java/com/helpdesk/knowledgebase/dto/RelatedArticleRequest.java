package com.helpdesk.knowledgebase.dto;

import jakarta.validation.constraints.NotNull;

/** Body for POST /api/articles/{id}/related, which links {id} to another article. */
public record RelatedArticleRequest(

        @NotNull
        Long relatedArticleId
) {
}
