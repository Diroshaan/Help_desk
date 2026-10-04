package com.helpdesk.knowledgebase.repository;

import com.helpdesk.knowledgebase.entity.ArticleBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArticleBookmarkRepository extends JpaRepository<ArticleBookmark, Long> {

    boolean existsByStudentIdAndArticleId(Long studentId, Long articleId);

    Optional<ArticleBookmark> findByStudentIdAndArticleId(Long studentId, Long articleId);

    // studentId always comes from the session, so students only see their own list
    List<ArticleBookmark> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}
