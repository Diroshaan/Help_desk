package com.helpdesk.knowledgebase.dto;

import com.helpdesk.common.user.entity.Officer;
import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ArticleDetailResponse.from: students only see published related articles, officers see them all. */
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

        // Student view: the draft must not appear.
        ArticleDetailResponse studentView = ArticleDetailResponse.from(main, true);
        assertThat(studentView.relatedArticles())
                .extracting(ArticleSummaryResponse::id)
                .containsExactly(2L);

        // Officer view: both appear, since officers manage the links.
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

        assertThat(ArticleDetailResponse.from(main).relatedArticles())
                .extracting(ArticleSummaryResponse::id)
                .containsExactly(3L);
    }
}
