package com.helpdesk.knowledgebase.service;

import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleStatus;
import com.helpdesk.knowledgebase.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F5-N3 and F5-N4.
 *
 * All three tests stay at the repository-mock level rather than hitting a
 * real database - they confirm this service escapes and bounds correctly
 * before a query is ever issued. They do NOT confirm that MySQL/H2 actually
 * honours ESCAPE '\\' the way SQL says it should - that half lives in
 * ArticleRepository and is a real-database question, which is exactly what
 * a Postman run against a running app (docs/f5-postman-checks.md, filled in
 * for real, not assumed) is for.
 */
@ExtendWith(MockitoExtension.class)
class ArticleSearchServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    private ArticleSearchService service;

    @BeforeEach
    void setUp() {
        service = new ArticleSearchService(articleRepository);
    }

    @Test
    void search_escapesPercentWildcard_soHundredPercentDoesNotMatchHundredFollowedByAnything() {
        when(articleRepository.searchOneTerm(anyString())).thenReturn(List.of());
        ArgumentCaptor<String> termCaptor = ArgumentCaptor.forClass(String.class);

        service.search("100%", null, PageRequest.of(0, 20));

        verify(articleRepository).searchOneTerm(termCaptor.capture());
        // The term that reaches the repository must have % escaped to \%,
        // which is what makes the ESCAPE '\\' clause on the LIKE in
        // ArticleRepository.searchOneTerm treat it as a literal character
        // rather than a wildcard. Without this, "100%" as a search term
        // would match "1000", "100 anything", etc.
        assertThat(termCaptor.getValue()).isEqualTo("100\\%");
    }

    @Test
    void search_capsAtFiveDistinctTerms_evenWhenQueryHasMore() {
        when(articleRepository.searchOneTerm(anyString())).thenReturn(List.of());

        service.search("alpha beta gamma delta epsilon zeta eta theta", null, PageRequest.of(0, 20));

        // Eight distinct words in the query; only the first five distinct
        // terms may ever reach a repository call - this is what actually
        // stops a pasted 2,000-word query from running 2,000 full scans.
        verify(articleRepository, times(5)).searchOneTerm(anyString());
    }

    @Test
    void search_pageFarBeyondResults_returnsEmptyPageRatherThanError() {
        Article onlyMatch = publishedArticle(1L);
        when(articleRepository.searchOneTerm(anyString())).thenReturn(List.of(onlyMatch));

        // One match total; requesting page 100,000 is nowhere near it. The
        // old code cast pageable.getOffset() (a long) straight to int before
        // checking it was in range; this asks for a page whose offset is
        // comfortably past the single result, the simplest case that
        // behaviour needs to get right.
        Page<com.helpdesk.knowledgebase.dto.ArticleSummaryResponse> page =
                service.search("password", null, PageRequest.of(100_000, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    private Article publishedArticle(Long id) {
        Article article = new Article("title", "body", mock(com.helpdesk.common.user.entity.Officer.class));
        article.setStatus(ArticleStatus.PUBLISHED);
        ReflectionTestUtils.setField(article, "id", id);
        ReflectionTestUtils.setField(article, "updatedAt", LocalDateTime.now());
        return article;
    }
}
