package com.helpdesk.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Incoming body for POST /api/articles (create, as DRAFT) and
 * PUT /api/articles/{id} (edit title/body/tags/categories).
 *
 * No id, status, or author field - deliberately, the same anti-mass-assignment
 * shape used across this project (see profile/dto/RegistrationRequest.java):
 * the client cannot set an article's id, choose its own status, or claim
 * authorship of someone else's article, because there's no field on this
 * class to bind any of that into. Status only moves via the dedicated
 * publish/archive endpoints; author is always the officer resolved from the
 * session in ArticleAdminController.
 */
public record ArticleRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 10000)
        String body,

        // Optional - an article can be drafted before it's tagged.
        // Normalised (trim + lowercase, Locale.ROOT) in ArticleService, not
        // here, so the same normalisation logic isn't duplicated per caller.
        Set<String> tags,

        // DB requirement: "each article in one or more categories" - zero
        // was accepted before this (F5 DB requirement gap). @NotEmpty works
        // against the compact constructor below, which turns a null
        // categoryIds into Set.of(): without that normalisation, @NotEmpty
        // would only catch an explicit [] in the request body and let a
        // missing field through as null. @Size(max = 5) is a sanity bound,
        // not a spec requirement - an article genuinely spanning more than
        // five categories is more likely a mis-tagged article than a real
        // one. Resolved through ReferenceDataService.requireCategory(id) in
        // the service layer, which throws ResourceNotFoundException (-> 404)
        // for an id that doesn't exist, rather than silently ignoring it.
        @NotEmpty(message = "Choose at least one category")
        @Size(max = 5)
        Set<Long> categoryIds
) {
    public ArticleRequest {
        // Records are immutable, but callers may still pass null for the two
        // optional collections - normalise here so the service never has to
        // null-check them. categoryIds null -> Set.of() specifically is what
        // lets @NotEmpty catch an omitted field, not just an explicit [].
        if (tags == null) {
            tags = Set.of();
        }
        if (categoryIds == null) {
            categoryIds = Set.of();
        }
    }
}
