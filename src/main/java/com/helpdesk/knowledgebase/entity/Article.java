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
 * A knowledge-base FAQ article written by an officer. Has tags (element collection),
 * one or more categories, and directional links to related articles.
 */
@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // not unique - two desks may both have a "Password reset" guide
    @NotBlank
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotBlank
    @Size(max = 10000)
    @Column(name = "body", nullable = false, length = 10000)
    private String body;

    // Stored as VARCHAR, not a MySQL ENUM, because ddl-auto=update won't alter an
    // existing enum column if we add a status later.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ArticleStatus status = ArticleStatus.DRAFT;

    // LAZY so loading an article doesn't always query the officer; fetch-joined when needed
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_officer_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_article_author"))
    private Officer author;

    // Tags are just words owned by the article, so an element collection is enough.
    // A Set stops the same tag being added twice.
    @ElementCollection
    @CollectionTable(name = "article_tags",
            joinColumns = @JoinColumn(name = "article_id"),
            foreignKey = @ForeignKey(name = "fk_article_tag_article"))
    @Column(name = "tag", length = 40, nullable = false)
    private Set<String> tags = new HashSet<>();

    // Owned from this side so the shared Category class doesn't depend on knowledgebase.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "article_categories",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"),
            foreignKey = @ForeignKey(name = "fk_article_category_article"),
            inverseForeignKey = @ForeignKey(name = "fk_article_category_category"))
    private Set<Category> categories = new HashSet<>();

    // Directional: A -> B doesn't mean B -> A. Linking an article to itself is
    // rejected in ArticleService.
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
