
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
     *
     * countQuery is explicit rather than Spring-derived. Left to derive its
     * own count from the SELECT above, Spring strips ORDER BY but keeps the
     * LEFT JOIN a.categories - without a matching DISTINCT in the derived
     * COUNT, an article in three categories counts as three rows, so
     * Page.getTotalElements() (and therefore getTotalPages()) overcounts
     * every time a category filter or multi-category article is involved.
     * COUNT(DISTINCT a) is the fix; it has to be spelled out here because
     * Spring's derivation doesn't add DISTINCT to the count side on its own.
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
     * use an index, so this is a full scan of title/body/tags on every call,
     * and it also returns every match unpaged - ArticleSearchService pages
     * the intersected result set in Java, not here. Fine at the scale of a
     * university help desk's FAQ (low hundreds of articles); wrong at scale.
     * The production fix is a MySQL FULLTEXT index with MATCH ... AGAINST,
     * which needs a raw SQL query rather than JPQL - left as a documented
     * follow-up, not built, because it's not required for this dataset size
     * and would add a MySQL-only code path.
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
     * Re-fetches a specific, already-paged set of articles with their
     * author, tags and categories eagerly loaded, in one round trip each,
     * instead of letting each ArticleSummaryResponse/ArticleDetailResponse
     * trigger its own lazy-load queries (the classic "load 20 articles, then
     * touch getCategories() on each" N+1 this project has flagged elsewhere -
     * see CategoryRepository's JOIN FETCH pattern, which this copies).
     *
     * LEFT JOIN FETCH a.author was missing from the original version of this
     * query: categories and tags were fetch-joined but author is also LAZY,
     * and both DTOs call getAuthor().getFullName() - so a 20-article page
     * was still firing 20 extra selects for the one collection this query
     * claimed to have already solved for. Fetching author here closes that
     * gap. It's a to-one join (unlike categories/tags), so it doesn't add to
     * the row-multiplication cartesian product below - only the two
     * collection fetches do that.
     *
     * Two LEFT JOIN FETCHes on two different Set-typed collections is safe
     * here (no MultipleBagFetchException - that only applies to List-typed
     * collections); DISTINCT dedupes the parent Article rows produced by the
     * join-table cross product. Worth knowing for the viva: an article with
     * 3 categories and 5 tags produces 15 raw rows here before Hibernate
     * folds them back into one Article with both collections populated -
     * fine at FAQ scale, and DISTINCT only dedupes the parent rows returned,
     * it doesn't reduce the row count fetched from the database.
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