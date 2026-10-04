package com.helpdesk.admin.entity;

import com.helpdesk.common.user.entity.Administrator;
import com.helpdesk.common.user.entity.Role;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A system-wide notice published by an administrator: title, body, publish and expiry
 * dates, who published it, and which roles may see it.
 * Author: Perera L. S. N. / Sanuthmi Niwetha (IT25103172)
 */
@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Size matches the column length so a long title is a clear 400, not a DB error.
    @NotBlank(message = "Announcement title is required")
    @Size(max = 200, message = "Title must be 200 characters or fewer")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotBlank(message = "Announcement body is required")
    @Size(max = 4000, message = "Body must be 4000 characters or fewer")
    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    // Set by the server, never by the client.
    @NotNull
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt = LocalDateTime.now();

    // Null means the notice never expires (e.g. standing opening hours).
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * A real foreign key, so the database rejects a publisher that doesn't exist.
     * Lazy so the student feed doesn't load the admin; the admin list uses JOIN FETCH.
     */
    @NotNull(message = "An announcement must have a publishing administrator")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_by", nullable = false,
                foreignKey = @ForeignKey(name = "fk_announcement_admin"))
    private Administrator publishedBy;

    /**
     * Roles allowed to see this notice. An @ElementCollection because a role here is a
     * plain value owned by the announcement (a multivalued attribute in the EER), not an
     * entity. Stored as VARCHAR so adding a new Role later doesn't break the MySQL column.
     * BatchSize loads the roles for up to 50 announcements in one query.
     */
    @BatchSize(size = 50)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "announcement_visible_roles",
            joinColumns = @JoinColumn(name = "announcement_id"),
            foreignKey = @ForeignKey(name = "fk_announcement_role_announcement")
    )
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", length = 20, nullable = false)
    private Set<Role> visibleToRoles = new HashSet<>();

    public Announcement() {
        // needed by JPA
    }

    public Announcement(String title, String body, LocalDateTime expiresAt,
                        Administrator publishedBy, Set<Role> visibleToRoles) {
        this.title = title;
        this.body = body;
        this.expiresAt = expiresAt;
        this.publishedBy = publishedBy;
        this.publishedAt = LocalDateTime.now();
        if (visibleToRoles != null) {
            this.visibleToRoles = new HashSet<>(visibleToRoles);
        }
    }

    // An empty role set means everyone can see it, so general notices need no role rows.
    public boolean isVisibleTo(Role role) {
        return visibleToRoles.isEmpty() || visibleToRoles.contains(role);
    }

    /** Live means already published and not yet expired. */
    public boolean isLiveAt(LocalDateTime moment) {
        return !publishedAt.isAfter(moment)
                && (expiresAt == null || expiresAt.isAfter(moment));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Administrator getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(Administrator publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Set<Role> getVisibleToRoles() {
        return visibleToRoles;
    }

    // Change the managed set in place so Hibernate only writes the rows that changed.
    public void setVisibleToRoles(Set<Role> roles) {
        this.visibleToRoles.clear();
        if (roles != null) {
            this.visibleToRoles.addAll(roles);
        }
    }
}
