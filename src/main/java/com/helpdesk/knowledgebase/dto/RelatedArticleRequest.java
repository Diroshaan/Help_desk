package com.helpdesk.knowledgebase.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body for POST /api/articles/{id}/related - links {id} to another article.
 *
 * NOTE for review: F5_Knowledge_Base_Spec.md section 3.6 describes the
 * relatedArticles relationship in detail (directional, self-reference must be
 * rejected) but section 4's API contract never lists an endpoint for it, and
 * neither does the build guide's endpoint table. This DTO existing in the
 * file manifest is the only signal an endpoint is expected. The shape below
 * is this implementation's best reading of gate 9 ("link two articles, fetch
 * the detail response") - confirm with Diroshaan that
 * POST /api/articles/{id}/related is the path he wants before the frontend is
 * built against it, since a changed path breaks his code same as any other
 * endpoint. The corresponding unlink, DELETE /api/articles/{id}/related/{relatedId},
 * has no request body - it doesn't need this DTO, only the path.
 */
public record RelatedArticleRequest(

        @NotNull
        Long relatedArticleId
) {
}
