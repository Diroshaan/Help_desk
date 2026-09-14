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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * F6 - System Analytics, Provisioning & Announcements
 * (Perera L. S. N. / Sanuthmi Niwetha, IT25103172)
 *
 * A system-wide notice banner published by an administrator.
 *
 * Requirement specification 3.2, Governance & Announcement Data:
 *
 *   "The system must store each announcement with a unique identifier, title,
 *    body, publication date and expiry date."
 *   "The system must record the administrator who published each announcement."
 *   "The system must store, against each announcement, the set of user roles
 *    permitted to view it."
 *
 * Those three sentences are requirements 2, 3 and 4, and this one class answers
 * all three: the scalar columns, the publishedBy foreign key, and the
 * visibleToRoles collection table respectively.
 */
@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @Size(max = 200) deliberately matches @Column(length = 200).
     *
     * A column length with no matching @Size is a trap this codebase has fallen
     * into three times already (Ticket.description, Feedback.comment,
     * BookmarkFolder.name): over-length input passes bean validation, fails at
     * the database instead, and GlobalExceptionHandler reports the resulting
     * DataIntegrityViolationException as "That value is already in use by
     * another account" - wrong status, wrong message, and nothing pointing at
     * the real cause. Keeping the two numbers together turns that 409 into the
     * 400 it always should have been.
     */
    @NotBlank(message = "Announcement title is required")
    @Size(max = 200, message = "Title must be 200 characters or fewer")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotBlank(message = "Announcement body is required")
    @Size(max = 4000, message = "Body must be 4000 characters or fewer")
    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    /** The publication date requirement 2 asks for. Set server-side, never by the client. */
    @NotNull
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt = LocalDateTime.now();

    /**
     * The expiry date, and it is nullable on purpose.
     *
     * Some notices are genuinely permanent - a standing "the help desk closes
     * at 4pm on Fridays" has no end date, and a NOT NULL column would force the
     * administrator to invent one far in the future. NULL is read everywhere in
     * this class and in AnnouncementRepository as "never expires", and the
     * queries are written to handle it explicitly rather than relying on SQL's
     * three-valued logic to do something reasonable by accident.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Requirement 3: the administrator who published this announcement.
     *
     * A real @ManyToOne rather than a plain Long admin id, so Hibernate emits an
     * actual FOREIGN KEY constraint and the database itself refuses an
     * announcement attributed to an administrator who does not exist. The
     * specification asks for exactly that under Data Integrity, and this is the
     * same reasoning as Category.department - see the long comment there.
     *
     * FetchType.LAZY, not the @ManyToOne default of EAGER. EAGER means every
     * query that loads an announcement silently fires a second query for its
     * administrator whether or not anybody reads it. The admin listing needs the
     * publisher's name, so AnnouncementRepository uses JOIN FETCH where it is
     * wanted; the student-facing feed does not, and does not pay for it.
     *
     * optional = false: an announcement with no publisher is not a meaningful
     * row - requirement 3 exists precisely so every notice is attributable.
     */
    @NotNull(message = "An announcement must have a publishing administrator")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_by", nullable = false,
                foreignKey = @ForeignKey(name = "fk_announcement_admin"))
    private Administrator publishedBy;

    /**
     * Requirement 4: the set of user roles permitted to view this announcement.
     *
     * WHY THIS IS AN @ElementCollection AND NOT A Role ENTITY
     * ------------------------------------------------------
     * A role listed here has no identity and no lifecycle of its own. There is
     * no "announcement role" anyone can look up, rename or refer to from
     * somewhere else; it is a value owned by the announcement, and deleting the
     * announcement should delete its rows with it. That is the definition of a
     * multivalued attribute in the EER model, and @ElementCollection is its
     * direct relational translation - a separate table keyed by the owning
     * announcement, exactly as the EER would draw it. Making it an entity would
     * invent an identity the domain does not have and leave orphan rows behind
     * on every delete. This is the simpler model AND the correct one, not a
     * shortcut taken to save work.
     *
     * WHY Set AND NOT List
     * --------------------
     * "Visible to STUDENT twice" is meaningless. A Set makes the duplicate
     * unrepresentable instead of merely unusual, and a unique constraint is not
     * needed to say so.
     *
     * WHY @JdbcTypeCode(SqlTypes.VARCHAR)
     * -----------------------------------
     * On MySQL, Hibernate 6 maps an @Enumerated(STRING) field to the database's
     * NATIVE enum type, baking the current constant list into the column
     * definition. ddl-auto=update never ALTERs an existing column, so adding a
     * fourth Role later would compile, deploy, start cleanly, and then fail on
     * every insert with a data-truncation error that never mentions the
     * constraint. Ticket.status is sitting on that landmine today;
     * ActivityLog.type shows this fix. Forcing VARCHAR stores the same text and
     * survives a new constant.
     */
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

    // --- Constructors ---

    public Announcement() {
        // Required no-argument constructor for JPA
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

    // --- Derived state ---

    /**
     * THE EMPTY-SET DECISION, stated explicitly because it has to be.
     *
     * An empty visibleToRoles means visible to EVERY role, not visible to
     * nobody. The alternative - writing all three roles onto every general
     * notice - is equally defensible, and the only indefensible option is
     * leaving it unwritten so that two people reading the same table disagree
     * about what an empty set means.
     *
     * This reading was chosen because "no restriction" is the honest description
     * of an announcement whose author never restricted it, and because it keeps
     * the common case (a notice for everyone) at zero rows in the join table
     * rather than three. The cost is that the query in AnnouncementRepository
     * has to spell out "empty OR contains" rather than just "contains", which is
     * one extra clause in one place.
     *
     * Note that "visible to nobody" is not a state anyone needs: an announcement
     * nobody may read is an announcement that should not have been published.
     */
    public boolean isVisibleTo(Role role) {
        return visibleToRoles.isEmpty() || visibleToRoles.contains(role);
    }

    /** Whether this notice is still live at the given moment. Null expiry never expires. */
    public boolean isLiveAt(LocalDateTime moment) {
        return !publishedAt.isAfter(moment)
                && (expiresAt == null || expiresAt.isAfter(moment));
    }

    // --- Getters and setters ---

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

    /**
     * Replaces the contents of the collection rather than swapping the field.
     *
     * Assigning a brand-new Set here would detach the instance Hibernate is
     * tracking, and on an UPDATE that produces the same end state by deleting
     * every row and re-inserting it - or, with some mappings, an
     * "A collection with cascade=all-delete-orphan was no longer referenced"
     * failure. Mutating the managed collection lets Hibernate work out the
     * actual difference and write only what changed.
     */
    public void setVisibleToRoles(Set<Role> roles) {
        this.visibleToRoles.clear();
        if (roles != null) {
            this.visibleToRoles.addAll(roles);
        }
    }
}
