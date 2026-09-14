package com.helpdesk.knowledgebase.repository;

import com.helpdesk.knowledgebase.entity.ArticleBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleBookmarkRepository extends JpaRepository<ArticleBookmark, Long> {

    boolean existsByStudentIdAndArticleId(Long studentId, Long articleId);

    Optional<ArticleBookmark> findByStudentIdAndArticleId(Long studentId, Long articleId);

    // Caller's own bookmarks only - ArticleBookmarkController always derives
    // studentId from the session, never from a request parameter, so this
    // query can never be used to read another student's saved list.
    List<ArticleBookmark> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}
