package com.helpdesk.knowledgebase.dto;

import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F5-N1. Officer is mocked rather than constructed for real - these tests
 * only need getFullName() to return something, and mocking means they don't
 * need to know Officer's actual constructor or field shape, which belongs to
 * a different package.
 *
 * id is set via ReflectionTestUtils because Article deliberately has no
 * setId() (see the class comment on Article) - every other test in this
 * package that needs an identifiable Article does the same.
 */
class ArticleDetailResponseTest {

    @Test
    void publishedRelatedOnly_hidesDraftFromStudents_showsItToOfficers() {
        Officer author = mock(Officer.class);
        when(author.getFullName()).thenReturn("Jane Officer");

        Article main = new Article("Main article", "body text here", author);

        Article relatedPublished = new Article("Related, published", "body", author);
        relatedPublished.setStatus(ArticleStatus.PUBLISHED);
        ReflectionTestUtils.setField(relatedPublished, "id", 2L);

        Article relatedDraft = new Article("Related, still a draft", "body", author);
        relatedDraft.setStatus(ArticleStatus.DRAFT);
        ReflectionTestUtils.setField(relatedDraft, "id", 3L);

        main.getRelatedArticles().add(relatedPublished);
        main.getRelatedArticles().add(relatedDraft);

        // Student view (publishedRelatedOnly = true, the callerIsStudent path
        // from ArticleService.getById): the draft must not appear.
        ArticleDetailResponse studentView = ArticleDetailResponse.from(main, true);
        assertThat(studentView.relatedArticles())
                .extracting(ArticleSummaryResponse::id)
                .containsExactly(2L);

        // Officer view (publishedRelatedOnly = false): both appear - managing
        // the links is the point of this view.
        ArticleDetailResponse officerView = ArticleDetailResponse.from(main, false);
        assertThat(officerView.relatedArticles())
                .extracting(ArticleSummaryResponse::id)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void singleArgFrom_behavesLikeOfficerView() {
        Officer author = mock(Officer.class);
        when(author.getFullName()).thenReturn("Jane Officer");

        Article main = new Article("Main article", "body text here", author);
        Article relatedDraft = new Article("Related, still a draft", "body", author);
        relatedDraft.setStatus(ArticleStatus.DRAFT);
        ReflectionTestUtils.setField(relatedDraft, "id", 3L);
        main.getRelatedArticles().add(relatedDraft);

        // from(article) with no boolean must still show everything - it's
        // the "show all" overload create()/update() rely on.
        assertThat(ArticleDetailResponse.from(main).relatedArticles())
                .extracting(ArticleSummaryResponse::id)
                .containsExactly(3L);
    }
}
