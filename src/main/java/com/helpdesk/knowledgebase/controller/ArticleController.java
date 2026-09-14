package com.helpdesk.knowledgebase.controller;

import com.helpdesk.knowledgebase.dto.ArticleDetailResponse;
import com.helpdesk.knowledgebase.dto.ArticleSummaryResponse;
import com.helpdesk.knowledgebase.service.ArticleSearchService;
import com.helpdesk.knowledgebase.service.ArticleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only, student-facing endpoints. Both fall through to
 * .anyRequest().authenticated() in SecurityConfig - correct, since published
 * articles are for logged-in students, and there's nothing here an officer
 * or admin shouldn't also be able to call. The create/edit/publish/archive
 * endpoints live in ArticleAdminController, gated to hasRole("OFFICER").
 */
@RestController
public class ArticleController {

    private final ArticleSearchService searchService;
    private final ArticleService articleService;

    public ArticleController(ArticleSearchService searchService, ArticleService articleService) {
        this.searchService = searchService;
        this.articleService = articleService;
    }

    /**
     * GET /api/articles?q=&category=&page=0&size=20 - PUBLISHED only. Both
     * q and category are optional; q missing means "everything published",
     * not an error (F5_Knowledge_Base_Spec.md section 6).
     */
    @GetMapping("/api/articles")
    public Page<ArticleSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchService.search(q, category, PageRequest.of(page, size));
    }

    /**
     * GET /api/articles/{id} - 404 if absent, or if the article is DRAFT/
     * ARCHIVED and the caller is a student (ArticleService.getById enforces
     * this). An officer or admin sees any status, which is what lets an
     * officer preview a draft through the same endpoint the student app uses.
     */
    @GetMapping("/api/articles/{id}")
    public ArticleDetailResponse getById(@PathVariable Long id, Authentication authentication) {
        return articleService.getById(id, isStudentOnly(authentication));
    }

    /**
     * True only when the caller has no OFFICER or ADMIN authority. Officers
     * provisioned through F6 and administrators both need to see unpublished
     * articles through this same path (an officer previewing their own
     * draft), so the restriction is "student and nothing else", not "not an
     * officer".
     */
    private boolean isStudentOnly(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(a -> a.equals("ROLE_OFFICER") || a.equals("ROLE_ADMIN"));
    }
}
