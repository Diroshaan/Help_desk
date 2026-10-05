package com.helpdesk.knowledgebase.controller;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.common.user.repository.OfficerRepository;
import com.helpdesk.knowledgebase.dto.ArticleDetailResponse;
import com.helpdesk.knowledgebase.dto.ArticleRequest;
import com.helpdesk.knowledgebase.dto.ArticleSummaryResponse;
import com.helpdesk.knowledgebase.dto.RelatedArticleRequest;
import com.helpdesk.knowledgebase.service.ArticleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Officer endpoints for writing and managing articles. Each path is limited to
 * OFFICER in SecurityConfig.
 */
@RestController
public class ArticleAdminController {

    private final ArticleService articleService;
    private final OfficerRepository officerRepository;

    public ArticleAdminController(ArticleService articleService, OfficerRepository officerRepository) {
        this.articleService = articleService;
        this.officerRepository = officerRepository;
    }

    @PostMapping("/api/articles")
    public ResponseEntity<ArticleDetailResponse> create(@Valid @RequestBody ArticleRequest request,
                                                          Authentication authentication,
                                                          UriComponentsBuilder uriBuilder) {
        Officer author = currentOfficer(authentication);
        ArticleDetailResponse created = articleService.create(request, author);
        var location = uriBuilder.path("/api/articles/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/api/articles/{id}")
    public ArticleDetailResponse update(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        return articleService.update(id, request);
    }

    @PostMapping("/api/articles/{id}/publish")
    public ArticleDetailResponse publish(@PathVariable Long id) {
        articleService.publish(id);
        return articleService.getById(id, false);
    }

    @PostMapping("/api/articles/{id}/archive")
    public ArticleDetailResponse archive(@PathVariable Long id) {
        articleService.archive(id);
        return articleService.getById(id, false);
    }

    @PostMapping("/api/articles/{id}/related")
    public ArticleDetailResponse addRelated(@PathVariable Long id, @Valid @RequestBody RelatedArticleRequest request) {
        articleService.addRelated(id, request.relatedArticleId());
        return articleService.getById(id, false);
    }

    // removes the {id} -> {relatedId} link only, not the reverse
    @DeleteMapping("/api/articles/{id}/related/{relatedId}")
    public ResponseEntity<Void> removeRelated(@PathVariable Long id, @PathVariable Long relatedId) {
        articleService.removeRelated(id, relatedId);
        return ResponseEntity.noContent().build();
    }

    // all statuses, with the same page/size limits as the public list
    @GetMapping("/api/articles/manage")
    public Page<ArticleSummaryResponse> manage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return articleService.listForManagement(
                PageRequest.of(PageBounds.clampPage(page), PageBounds.clampSize(size)));
    }

    // the session principal name is the officer's email
    private Officer currentOfficer(Authentication authentication) {
        return officerRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No officer account for " + authentication.getName()));
    }
}
