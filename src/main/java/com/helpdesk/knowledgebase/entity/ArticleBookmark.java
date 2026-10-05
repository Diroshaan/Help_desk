package com.helpdesk.knowledgebase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * A student's saved article. Its own entity (not @ManyToMany) because it also stores
 * when it was saved. The unique constraint stops double bookmarks, since a check in
 * Java alone can be raced by two quick clicks.
 */
@Entity
@Table(name = "article_bookmarks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_article_bookmark_student_article",
                columnNames = {"student_id", "article_id"}))
public class ArticleBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ArticleBookmark() {
        // JPA only.
    }

    public ArticleBookmark(Long articleId, Long studentId) {
        this.articleId = articleId;
        this.studentId = studentId;
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getArticleId() {
        return articleId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
