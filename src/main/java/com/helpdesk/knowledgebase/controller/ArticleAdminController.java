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
 * Officer-facing authoring endpoints. Every mapping here needs a matching
 * hasRole("OFFICER") line in SecurityConfig, which this project's house
 * rule says not to edit directly - the block below is what to send
 * Diroshaan, extended from F5_Knowledge_Base_Spec.md section 4 with the two
 * /related matchers (not in the original API contract - see the note on
 * RelatedArticleRequest and addRelated/removeRelated below):
 *
 * <pre>
 * .requestMatchers(HttpMethod.POST,   "/api/articles").hasRole("OFFICER")
 * .requestMatchers(HttpMethod.PUT,    "/api/articles/**").hasRole("OFFICER")
 * .requestMatchers("/api/articles/*&#47;publish", "/api/articles/*&#47;archive").hasRole("OFFICER")
 * .requestMatchers(HttpMethod.GET,    "/api/articles/manage").hasRole("OFFICER")
 * .requestMatchers(HttpMethod.POST,   "/api/articles/*&#47;related").hasRole("OFFICER")
 * .requestMatchers(HttpMethod.DELETE, "/api/articles/*&#47;related/**").hasRole("OFFICER")
 * </pre>
 *
 * Until those lines land, every path here falls through to
 * .anyRequest().authenticated() - any logged-in user can call them, not only
 * officers. That's the documented, temporary state the build guide describes
 * ("test the officer endpoints by logging in as the seeded officer; the
 * catch-all lets any authenticated user through, so nothing blocks you").
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

    /**
     * Unlinks {id} from {relatedId}, one direction only. A link made by
     * mistake, or one that's stopped being true, needs a way back out -
     * addRelated with no corresponding remove would make every link
     * permanent.
     */
    @DeleteMapping("/api/articles/{id}/related/{relatedId}")
    public ResponseEntity<Void> removeRelated(@PathVariable Long id, @PathVariable Long relatedId) {
        articleService.removeRelated(id, relatedId);
        return ResponseEntity.noContent().build();
    }

    /** All statuses, for the authoring workspace - see F5_Build_Guide.md gate list, step 3. */
    @GetMapping("/api/articles/manage")
    public Page<ArticleSummaryResponse> manage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return articleService.listForManagement(PageRequest.of(page, size));
    }

    /**
     * Bridges the logged-in session to a managed Officer entity, the same
     * way BookmarkFolderController.currentStudentId() bridges to a Student -
     * authentication.getName() is the account's email (set as the Spring
     * Security principal name for students; assumed to be set the same way
     * for officers, since no separate OfficerUserDetailsService is documented
     * anywhere in the project's specs).
     *
     * Depends on OfficerRepository.findByEmail(String), which does not exist
     * on develop yet - Diroshaan is adding it (plus Officer.getFullName(),
     * used in ArticleSummaryResponse/ArticleDetailResponse) in his own PR to
     * the shared common/user package, spelled exactly as used here. This
     * branch won't compile until that PR merges - see F5_Implementation_Notes.md
     * "Sequencing" for the merge order.
     */
    private Officer currentOfficer(Authentication authentication) {
        return officerRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No officer account for " + authentication.getName()));
    }
}
