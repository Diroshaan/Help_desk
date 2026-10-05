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
 * Read-only article endpoints for any logged-in user. Writing articles is in
 * ArticleAdminController.
 */
@RestController
public class ArticleController {

    private final ArticleSearchService searchService;
    private final ArticleService articleService;

    public ArticleController(ArticleSearchService searchService, ArticleService articleService) {
        this.searchService = searchService;
        this.articleService = articleService;
    }

    // Search published articles. q and category are optional; page and size are clamped.
    @GetMapping("/api/articles")
    public Page<ArticleSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchService.search(q, category,
                PageRequest.of(PageBounds.clampPage(page), PageBounds.clampSize(size)));
    }

    // Students get 404 for drafts and archived articles; officers and admins see any status.
    @GetMapping("/api/articles/{id}")
    public ArticleDetailResponse getById(@PathVariable Long id, Authentication authentication) {
        return articleService.getById(id, isStudentOnly(authentication));
    }

    // true unless the caller has the OFFICER or ADMIN role
    private boolean isStudentOnly(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(a -> a.equals("ROLE_OFFICER") || a.equals("ROLE_ADMIN"));
    }
}
