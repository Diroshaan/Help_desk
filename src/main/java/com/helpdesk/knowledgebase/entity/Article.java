package com.helpdesk.knowledgebase.entity;

import com.helpdesk.common.reference.entity.Category;
import com.helpdesk.common.user.entity.Officer;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A knowledge-base FAQ article. Requirement spec 3.2 #1.
 *
 * Five distinct relationship patterns live on this one entity, deliberately -
 * this is the feature that exercises the widest range of modelling patterns
 * in the whole project (F5_Knowledge_Base_Spec.md section 2):
 *   - status            enum column that survives a MySQL migration
 *   - author            a real @ManyToOne foreign key to Officer
 *   - tags              @ElementCollection, a multivalued attribute
 *   - categories        @ManyToMany, owned from this side
 *   - relatedArticles   self-referencing @ManyToMany, directional
 */
@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Not unique: two desks may legitimately both publish a "Password reset" guide.
    @NotBlank
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // Bounded rather than @Lob: 10,000 characters is far more than any FAQ
    // article needs, and a bounded column can still be indexed and compared.
    // @Lob is only the right call for genuinely unbounded text.
    @NotBlank
    @Size(max = 10000)
    @Column(name = "body", nullable = false, length = 10000)
    private String body;

    // @JdbcTypeCode(VARCHAR) forces MySQL to store this as varchar + CHECK
    // constraint instead of a native ENUM column. ddl-auto=update never
    // alters an existing column, so a native enum would make adding a fourth
    // status later a silent-start, every-insert-fails runtime bug rather than
    // a schema change. Same fix as profile/entity/ActivityLog.java.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ArticleStatus status = ArticleStatus.DRAFT;

    // LAZY, not the @ManyToOne default of EAGER - otherwise every article
    // load silently fires a second query for the officer whether the caller
    // needs the author or not. ArticleRepository fetch-joins it where it's
    // actually needed (the summary/detail hydration queries).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_officer_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_article_author"))
    private Officer author;

    // A tag has no identity and no attributes of its own - it's a word owned
    // by the article, and deleting the article should delete its tags. That
    // is exactly what @ElementCollection models. A separate Tag entity would
    // only be justified if tags had their own lifecycle (a description, a
    // colour, an administrator managing the vocabulary) - they don't, so the
    // simpler model is the correct one, not the lazy one.
    // Set, not List: the same tag twice on one article is meaningless, and
    // the resulting composite primary key (article_id, tag) enforces that at
    // the database level, not only in Java.
    @ElementCollection
    @CollectionTable(name = "article_tags",
            joinColumns = @JoinColumn(name = "article_id"),
            foreignKey = @ForeignKey(name = "fk_article_tag_article"))
    @Column(name = "tag", length = 40, nullable = false)
    private Set<String> tags = new HashSet<>();

    // Owned from Article. Category is shared reference data used by four
    // features; a mappedBy collection back to Article would make the shared
    // common/reference package depend on knowledgebase, which is backwards.
    // "All articles in category X" is a query in ArticleRepository, not a
    // field on Category.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "article_categories",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"),
            foreignKey = @ForeignKey(name = "fk_article_category_article"),
            inverseForeignKey = @ForeignKey(name = "fk_article_category_category"))
    private Set<Category> categories = new HashSet<>();

    // Self-referencing and deliberately directional: A -> B does not imply
    // B -> A. "See also, for background" often only makes sense one way (a
    // troubleshooting article pointing back at the setup guide it assumes you
    // read, not the setup guide pointing forward at every article that
    // assumes it) - so the direction an officer links in is the direction
    // that's stored. Self-reference (A -> A) is rejected in ArticleService,
    // not here: JPA has no way to generate a check constraint comparing the
    // two FK columns of the same join-table row against each other.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "article_related",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "related_article_id"),
            foreignKey = @ForeignKey(name = "fk_related_article"),
            inverseForeignKey = @ForeignKey(name = "fk_related_target"))
    private Set<Article> relatedArticles = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Article() {
        // JPA only.
    }

    public Article(String title, String body, Officer author) {
        this.title = title;
        this.body = body;
        this.author = author;
        this.status = ArticleStatus.DRAFT;
    }

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Same @PreUpdate pattern as ticketportal/entity/Feedback.java - Dakshin
    // already solved "refresh updatedAt on every save" for this project.
    @PreUpdate
    private void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public ArticleStatus getStatus() {
        return status;
    }

    public void setStatus(ArticleStatus status) {
        this.status = status;
    }

    public Officer getAuthor() {
        return author;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }

    public Set<Article> getRelatedArticles() {
        return relatedArticles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
