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

/** ArticleSearchService with the repository mocked: wildcard escaping, the term cap and page bounds. */
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
        // Escaped so the LIKE ... ESCAPE '\\' treats % as a literal, not a wildcard.
        assertThat(termCaptor.getValue()).isEqualTo("100\\%");
    }

    @Test
    void search_capsAtFiveDistinctTerms_evenWhenQueryHasMore() {
        when(articleRepository.searchOneTerm(anyString())).thenReturn(List.of());

        service.search("alpha beta gamma delta epsilon zeta eta theta", null, PageRequest.of(0, 20));

        // Eight words in, only five searches, so a pasted essay can't run thousands of scans.
        verify(articleRepository, times(5)).searchOneTerm(anyString());
    }

    @Test
    void search_pageFarBeyondResults_returnsEmptyPageRatherThanError() {
        Article onlyMatch = publishedArticle(1L);
        when(articleRepository.searchOneTerm(anyString())).thenReturn(List.of(onlyMatch));

        // Only one match, so this page is far past the end.
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
