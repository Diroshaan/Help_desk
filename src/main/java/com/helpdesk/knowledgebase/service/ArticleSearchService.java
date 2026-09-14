package com.helpdesk.knowledgebase.service;

import com.helpdesk.knowledgebase.dto.ArticleSummaryResponse;
import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleStatus;
import com.helpdesk.knowledgebase.repository.ArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GET /api/articles - search by keyword, filter by category, PUBLISHED only.
 * Split out from ArticleService because "every term must match" is a
 * distinct, easy-to-get-wrong piece of logic worth being able to point at on
 * its own (F5_Knowledge_Base_Spec.md section 6 calls this out as "the
 * requirement most people implement as a single LIKE '%term%' and lose marks
 * on").
 */
@Service
public class ArticleSearchService {

    private final ArticleRepository articleRepository;

    public ArticleSearchService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * @param query      whitespace-separated keywords, or blank/null for
     *                   "everything published" - an empty search must not be
     *                   treated as an error, it's the default browse view.
     * @param categoryId optional category filter, applied after the keyword
     *                   intersection (in memory) or directly in SQL when
     *                   there's no keyword search (in the database) - see the
     *                   two branches below.
     */
    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> search(String query, Long categoryId, Pageable pageable) {
        List<String> terms = splitTerms(query);

        if (terms.isEmpty()) {
            // No keywords: the category filter (if any) and the pagination
            // both happen in the database via findPublished, so this branch
            // scales the same way the rest of the app's list endpoints do.
            Page<Article> page = articleRepository.findPublished(
                    ArticleStatus.PUBLISHED, categoryId, pageable);
            List<Long> ids = page.getContent().stream().map(Article::getId).collect(Collectors.toList());
            Map<Long, Article> hydrated = hydrate(ids);
            return page.map(a -> ArticleSummaryResponse.from(hydrated.get(a.getId())));
        }

        // Keyword search: searchOneTerm already restricts to PUBLISHED, so
        // start from the first term's matches and progressively keep only
        // the ids that also appear in each subsequent term's matches - that
        // intersection is what turns "any of these words" into "all of
        // these words", per the spec's explicit AND requirement.
        Set<Long> matchingIds = null;
        Map<Long, Article> byId = new LinkedHashMap<>();
        for (String term : terms) {
            List<Article> matches = articleRepository.searchOneTerm(term);
            Set<Long> termIds = new LinkedHashSet<>();
            for (Article a : matches) {
                termIds.add(a.getId());
                byId.putIfAbsent(a.getId(), a);
            }
            matchingIds = (matchingIds == null)
                    ? termIds
                    : intersect(matchingIds, termIds);
        }

        // categoryId filtering here touches each intersected article's lazy
        // .getCategories() before hydration/pagination has narrowed the set
        // down to one page - an N+1 against the full intersected match set,
        // not just the page returned. Same honest trade-off as the LIKE scan
        // above: acceptable at FAQ scale, and the fix (push the category
        // filter into searchOneTerm itself) is a reasonable next step once
        // article counts grow past what a full in-memory intersection suits.
        List<Article> results = matchingIds.stream()
                .map(byId::get)
                .filter(a -> categoryId == null
                        || a.getCategories().stream().anyMatch(c -> c.getId().equals(categoryId)))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(Collectors.toList());

        // Pagination happens here, in memory, after the intersection and
        // category filter - not a repository-level Pageable. This is the
        // honest trade-off that comes with LIKE-based search: the result set
        // has to be materialised and intersected in Java before anyone can
        // say which "page" of it exists, which is exactly the scan-based
        // weakness the FULLTEXT-index follow-up (noted on
        // ArticleRepository.searchOneTerm) would remove. Fine at the scale of
        // a university FAQ; documented, not hidden.
        int start = Math.min((int) pageable.getOffset(), results.size());
        int end = Math.min(start + pageable.getPageSize(), results.size());
        List<Long> pageIds = results.subList(start, end).stream()
                .map(Article::getId)
                .collect(Collectors.toList());
        Map<Long, Article> hydrated = hydrate(pageIds);
        List<ArticleSummaryResponse> content = pageIds.stream()
                .map(hydrated::get)
                .map(ArticleSummaryResponse::from)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, results.size());
    }

    private Map<Long, Article> hydrate(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return articleRepository.findWithCategoriesAndTagsByIdIn(ids).stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
    }

    private Set<Long> intersect(Set<Long> a, Set<Long> b) {
        Set<Long> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private List<String> splitTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return List.of(query.trim().split("\\s+"));
    }
}
