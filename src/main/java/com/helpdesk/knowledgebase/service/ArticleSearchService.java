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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keyword search over PUBLISHED articles, with an optional category filter.
 * An article must match every keyword, not just one.
 */
@Service
public class ArticleSearchService {

    private final ArticleRepository articleRepository;

    public ArticleSearchService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    // A blank query isn't an error - it lists every published article.
    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> search(String query, Long categoryId, Pageable pageable) {
        List<String> terms = splitTerms(query);

        if (terms.isEmpty()) {
            // no keywords: filter and page in the database
            Page<Article> page = articleRepository.findPublished(
                    ArticleStatus.PUBLISHED, categoryId, pageable);
            List<Long> ids = page.getContent().stream().map(Article::getId).collect(Collectors.toList());
            Map<Long, Article> hydrated = hydrate(ids);
            return page.map(a -> ArticleSummaryResponse.from(hydrated.get(a.getId())));
        }

        // Search each term separately and keep only ids found for every term (AND).
        Set<Long> matchingIds = null;
        Map<Long, Article> byId = new LinkedHashMap<>();
        for (String term : terms) {
            List<Article> matches = articleRepository.searchOneTerm(escapeLikeWildcards(term));
            Set<Long> termIds = new LinkedHashSet<>();
            for (Article a : matches) {
                termIds.add(a.getId());
                byId.putIfAbsent(a.getId(), a);
            }
            matchingIds = (matchingIds == null)
                    ? termIds
                    : intersect(matchingIds, termIds);
        }

        // Category filter runs in memory and lazy-loads categories per match; fine at FAQ size.
        List<Article> results = matchingIds.stream()
                .map(byId::get)
                .filter(a -> categoryId == null
                        || a.getCategories().stream().anyMatch(c -> c.getId().equals(categoryId)))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(Collectors.toList());

        // Paged in memory after intersecting. offset is checked as a long before the
        // int cast, so a huge page number gives an empty page instead of wrapping.
        long offset = pageable.getOffset();
        if (offset >= results.size()) {
            return new PageImpl<>(List.of(), pageable, results.size());
        }
        int start = (int) offset;
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

    /**
     * Lower-cases and de-duplicates the terms, cuts each to 50 characters and keeps at
     * most 5, since every term costs a full table scan.
     */
    private List<String> splitTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String raw : query.trim().split("\\s+")) {
            distinct.add(raw.toLowerCase(Locale.ROOT));
        }
        List<String> terms = new ArrayList<>();
        for (String term : distinct) {
            if (terms.size() >= 5) {
                break;
            }
            terms.add(term.length() > 50 ? term.substring(0, 50) : term);
        }
        return terms;
    }

    /**
     * Escapes the LIKE wildcards % and _ so "100%" is searched literally. Backslash is
     * doubled first so it can't combine with the escapes we add (matches ESCAPE '\\').
     */
    private String escapeLikeWildcards(String term) {
        return term.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
