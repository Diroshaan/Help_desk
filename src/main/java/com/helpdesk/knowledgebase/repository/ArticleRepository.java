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
     * Base listing: published only, optionally scoped to one category. Runs
     * over scalar/joined-id columns only (no collection fetch), so pagination
     * is a plain SQL LIMIT/OFFSET and there's no N+1 risk from this query
     * itself - see findWithCategoriesAndTagsByIdIn below for the hydration
     * step that avoids the N+1 when the summaries are actually built.
     */
    @Query("""
            SELECT DISTINCT a FROM Article a
            LEFT JOIN a.categories c
            WHERE a.status = :status
              AND (:categoryId IS NULL OR c.id = :categoryId)
            ORDER BY a.updatedAt DESC
            """)
    Page<Article> findPublished(@Param("status") ArticleStatus status,
                                 @Param("categoryId") Long categoryId,
                                 Pageable pageable);

    /**
     * All statuses, for the officer "manage" view (F5_Build_Guide.md step 3 /
     * spec section 4's /api/articles/manage). No author filter - any officer
     * can see every article regardless of who drafted it, matching the
     * project's existing pattern where OFFICER is one shared role rather than
     * per-desk ownership. Revisit if the department split ever needs
     * enforcing at the data layer.
     */
    Page<Article> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    /**
     * One search term across title, body and tags of PUBLISHED articles
     * only. ArticleSearchService calls this once per whitespace-split term
     * and intersects the id sets, so that an article must match every term,
     * not just one (F5_Knowledge_Base_Spec.md section 6).
     *
     * DISTINCT matters: the LEFT JOIN onto tags produces one row per matching
     * tag, and without DISTINCT the same article would come back multiple
     * times for a term that hits two of its own tags.
     *
     * Known weakness, said up front rather than hidden: LIKE '%term%' cannot
     * use an index, so this is a full scan of title/body/tags on every call.
     * Fine at the scale of a university help desk's FAQ (low hundreds of
     * articles); wrong at scale. The production fix is a MySQL FULLTEXT index
     * with MATCH ... AGAINST, which needs a raw SQL query rather than JPQL -
     * left as a documented follow-up, not built, because it's not required
     * for this dataset size and would add a MySQL-only code path.
     */
    @Query("""
            SELECT DISTINCT a FROM Article a
            LEFT JOIN a.tags t
            WHERE a.status = com.helpdesk.knowledgebase.entity.ArticleStatus.PUBLISHED
              AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(a.body)  LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(t)       LIKE LOWER(CONCAT('%', :term, '%')))
            """)
    List<Article> searchOneTerm(@Param("term") String term);

    /**
     * Re-fetches a specific, already-paged set of articles with their tags
     * and categories eagerly loaded, in one round trip each, instead of
     * letting each ArticleSummaryResponse trigger its own lazy-load queries
     * (the classic "load 20 articles, then touch getCategories() on each" N+1
     * this project has flagged elsewhere - see CategoryRepository's
     * JOIN FETCH pattern, which this copies).
     *
     * Two LEFT JOIN FETCHes on two different Set-typed collections is safe
     * here (no MultipleBagFetchException - that only applies to List-typed
     * collections); DISTINCT dedupes the parent Article rows produced by the
     * join-table cross product.
     */
    @Query("""
            SELECT DISTINCT a FROM Article a
            LEFT JOIN FETCH a.categories
            LEFT JOIN FETCH a.tags
            WHERE a.id IN :ids
            """)
    List<Article> findWithCategoriesAndTagsByIdIn(@Param("ids") List<Long> ids);
}
