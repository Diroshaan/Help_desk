package com.helpdesk.knowledgebase.service;

import com.helpdesk.common.exception.ResourceNotFoundException;
import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleBookmark;
import com.helpdesk.knowledgebase.entity.ArticleStatus;
import com.helpdesk.knowledgebase.dto.ArticleSummaryResponse;
import com.helpdesk.knowledgebase.repository.ArticleBookmarkRepository;
import com.helpdesk.knowledgebase.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** ArticleBookmarkService: only published articles can be bookmarked or listed. */
@ExtendWith(MockitoExtension.class)
class ArticleBookmarkServiceTest {

    @Mock
    private ArticleBookmarkRepository bookmarkRepository;
    @Mock
    private ArticleRepository articleRepository;

    private ArticleBookmarkService service;

    @BeforeEach
    void setUp() {
        service = new ArticleBookmarkService(bookmarkRepository, articleRepository);
    }

    @Test
    void bookmark_draftArticle_throwsResourceNotFound_andNeverReachesBookmarkRepository() {
        Article draft = articleWithStatus(10L, ArticleStatus.DRAFT);
        when(articleRepository.findById(10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.bookmark(10L, /* studentId */ 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        // The status check runs before any save or duplicate check.
        verifyNoInteractions(bookmarkRepository);
    }

    @Test
    void bookmark_nonexistentArticle_alsoThrowsResourceNotFound() {
        when(articleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookmark(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listBookmarked_dropsArchivedArticle_butKeepsPublishedOne() {
        ArticleBookmark publishedBookmark = bookmarkOf(100L);
        ArticleBookmark archivedBookmark = bookmarkOf(200L);
        when(bookmarkRepository.findByStudentIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(publishedBookmark, archivedBookmark));

        Article published = articleWithStatus(100L, ArticleStatus.PUBLISHED);
        Article archived = articleWithStatus(200L, ArticleStatus.ARCHIVED);
        when(articleRepository.findWithCategoriesAndTagsByIdIn(List.of(100L, 200L)))
                .thenReturn(List.of(published, archived));

        var result = service.listBookmarked(1L);

        // The bookmark row is kept; archived articles are only filtered out on read.
        assertThat(result).extracting(ArticleSummaryResponse::id).containsExactly(100L);
    }

    private Article articleWithStatus(Long id, ArticleStatus status) {
        Article article = new Article("title", "body", mock(com.helpdesk.common.user.entity.Officer.class));
        article.setStatus(status);
        ReflectionTestUtils.setField(article, "id", id);
        ReflectionTestUtils.setField(article, "updatedAt", LocalDateTime.now());
        return article;
    }

    private ArticleBookmark bookmarkOf(Long articleId) {
        return new ArticleBookmark(articleId, 1L);
    }
}