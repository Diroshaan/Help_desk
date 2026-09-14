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
 * A student's saved article. Requirement spec 3.2 #4: "a student bookmarks
 * many articles; an article is bookmarked by many students" - many-to-many,
 * but WITH an attribute (when it was saved), which a plain @ManyToMany
 * cannot carry. That's why this is its own entity rather than a join
 * annotation on Article or a student.
 *
 * studentId/articleId are stored as plain longs, matching the existing
 * ticketportal/entity/Bookmark.java shape in this project, rather than as
 * @ManyToOne relationships - keeps this table decoupled from both the
 * knowledgebase and profile packages owning each other.
 *
 * The unique constraint is the actual point of this class. Without it,
 * clicking "bookmark" twice inserts two rows and the article shows up twice
 * in the student's saved list. ticketportal/entity/Bookmark.java does a
 * check-then-act guard in BookmarkService with no constraint behind it - two
 * fast clicks both pass the "does it exist?" check before either insert
 * lands, and both succeed. A Java check can be raced; a database constraint
 * cannot. ArticleBookmarkService still does the Java check first (for a fast,
 * friendly error on the common case), but the constraint below is the real
 * defence, and DataIntegrityViolationException is caught as the backstop for
 * the race.
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
