package com.helpdesk.knowledgebase.repository;

import com.helpdesk.knowledgebase.entity.Article;
import com.helpdesk.knowledgebase.entity.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * Published articles, optionally in one category, paged in SQL.
     * The count query is written out with COUNT(DISTINCT a) so an article in several
     * categories isn't counted more than once.
     */
    @Query(value = """
            SELECT DISTINCT a FROM Article a
            LEFT JOIN a.categories c
            WHERE a.status = :status
              AND (:categoryId IS NULL OR c.id = :categoryId)
            ORDER BY a.updatedAt DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT a) FROM Article a
            LEFT JOIN a.categories c
            WHERE a.status = :status
              AND (:categoryId IS NULL OR c.id = :categoryId)
            """)
    Page<Article> findPublished(@Param("status") ArticleStatus status,
                                @Param("categoryId") Long categoryId,
                                Pageable pageable);

    // all statuses for the officer manage view; any officer sees every article
    Page<Article> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    /**
     * Matches one term in the title, body or tags of PUBLISHED articles only.
     * ESCAPE '\\' lets a search like "100%" match literally; the service escapes
     * % and _ first. LIKE '%term%' can't use an index, which is fine at FAQ size.
     */
    @Query("""
            SELECT DISTINCT a FROM Article a
            LEFT JOIN a.tags t
            WHERE a.status = com.helpdesk.knowledgebase.entity.ArticleStatus.PUBLISHED
              AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '\\'
                OR LOWER(a.body)  LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '\\'
                OR LOWER(t)       LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '\\')
            """)
    List<Article> searchOneTerm(@Param("term") String term);

    /**
     * Loads one page of articles with author, categories and tags in a single query,
     * so building the DTOs doesn't fire a lazy load per article (N+1). Both
     * collections are Sets, so two fetch joins are allowed.
     */
    @Query("""
            SELECT DISTINCT a FROM Article a
            LEFT JOIN FETCH a.author
            LEFT JOIN FETCH a.categories
            LEFT JOIN FETCH a.tags
            WHERE a.id IN :ids
            """)
    List<Article> findWithCategoriesAndTagsByIdIn(@Param("ids") List<Long> ids);
}