-- ===========================================================================
--  UNIHELP - Database demonstration
--  Read-only. Every statement is a SELECT or a DESCRIBE, so nothing here can
--  change or damage the database. Safe to run in front of anyone, any number
--  of times.
--
--  HOW TO RUN ONE QUERY: click anywhere inside it, then Ctrl+Enter.
--  Do NOT press Alt+X - that runs all of them at once and you lose the story.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- 1. "How many tables does the system have?"
--     Expect 22. Reads from the server, so no IDE cache can mislead you.
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = DATABASE();


-- ---------------------------------------------------------------------------
-- 2. "Show me them."
-- ---------------------------------------------------------------------------
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY table_name;


-- ---------------------------------------------------------------------------
-- 3. THE USER HIERARCHY - the most important query in this script.
--
--    Say while it runs: "The requirement asks for a single user record for
--    every actor, with role-specific data held only against the relevant type.
--    That is an EER specialisation, and we implemented it with JOINED
--    inheritance. Shared columns live once in `users`; each subtype has its
--    own table keyed by the same id."
--
--    Then point at the `role` column: "There is no role column anywhere in
--    the database. The role is derived from which table the row appears in,
--    so it cannot disagree with reality."
-- ---------------------------------------------------------------------------
SELECT u.id,
       u.email,
       u.active,
       CASE WHEN s.id IS NOT NULL THEN 'STUDENT'
            WHEN o.id IS NOT NULL THEN 'OFFICER'
            WHEN a.id IS NOT NULL THEN 'ADMIN'
       END AS role,
       COALESCE(s.full_name, o.full_name, a.display_name) AS name,
       COALESCE(s.student_id, o.staff_number, a.staff_number) AS reference
FROM users u
LEFT JOIN students       s ON s.id = u.id
LEFT JOIN officers       o ON o.id = u.id
LEFT JOIN administrators a ON a.id = u.id
ORDER BY role, u.id;


-- ---------------------------------------------------------------------------
-- 4. Proof that a subtype row cannot exist without its user row.
--    Every officer id must also be a users id - the foreign key guarantees it.
--    Expect 0 rows. An empty result is the correct answer here.
-- ---------------------------------------------------------------------------
SELECT o.id AS orphaned_officer
FROM officers o
LEFT JOIN users u ON u.id = o.id
WHERE u.id IS NULL;


-- ---------------------------------------------------------------------------
-- 5. Every foreign key in the schema, by name.
--
--    Say: "We named every constraint rather than letting Hibernate generate
--    something like FKq7x2m1k4d8s, so the schema is readable in the ER
--    diagram and an error message says what actually went wrong."
-- ---------------------------------------------------------------------------
SELECT constraint_name, table_name, column_name, referenced_table_name
FROM information_schema.key_column_usage
WHERE constraint_schema = DATABASE()
  AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name;


-- ---------------------------------------------------------------------------
-- 6. Every UNIQUE constraint - where the database, not the application,
--    enforces a business rule.
--
--    Point at `resolutions.ticket_id`: "The requirement says at most one
--    official resolution per ticket. That is a unique constraint, not a
--    check in Java, because two simultaneous requests can both pass a Java
--    check and only one can win against a database constraint."
-- ---------------------------------------------------------------------------
SELECT t.constraint_name, t.table_name,
       GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position) AS columns
FROM information_schema.table_constraints t
JOIN information_schema.key_column_usage k
  ON k.constraint_name = t.constraint_name
 AND k.constraint_schema = t.constraint_schema
WHERE t.constraint_schema = DATABASE()
  AND t.constraint_type = 'UNIQUE'
GROUP BY t.constraint_name, t.table_name
ORDER BY t.table_name;


-- ---------------------------------------------------------------------------
-- 7. The one fully enforced relationship chain: category -> department.
-- ---------------------------------------------------------------------------
SELECT d.code AS dept_code, d.name AS department, c.id AS category_id, c.name AS category
FROM categories c
JOIN departments d ON d.code = c.department_code
ORDER BY d.name, c.name;


-- ---------------------------------------------------------------------------
-- 8. Tickets with the student who raised them.
-- ---------------------------------------------------------------------------
SELECT t.id, t.subject, t.category, t.priority, t.status,
       s.full_name AS raised_by, t.created_at
FROM tickets t
JOIN students s ON s.id = t.student_id
ORDER BY t.created_at DESC
LIMIT 20;


-- ---------------------------------------------------------------------------
-- 9. DERIVED, NOT STORED - requirement 3.2, Resolution & Queue Data:
--    "must derive each ticket's resolution time from its submission and
--     resolution timestamps rather than storing it directly."
--
--    Say: "There is no resolution_time column. It is computed from two
--    timestamps we already record. Storing it would be the same fact twice,
--    in two places that can disagree."
-- ---------------------------------------------------------------------------
SELECT t.id, t.subject, t.created_at, t.resolved_at,
       TIMESTAMPDIFF(HOUR, t.created_at, t.resolved_at) AS resolution_hours
FROM tickets t
WHERE t.resolved_at IS NOT NULL
ORDER BY t.resolved_at DESC;


-- ---------------------------------------------------------------------------
-- 10. THE KNOWLEDGE BASE - four different relationship patterns in one query.
--
--     Say: "F5 exercises the widest range of relationship types in the
--     project: a multivalued attribute for tags, a many-to-many to
--     categories, a self-referencing many-to-many for related articles, and
--     a many-to-many with an attribute for student bookmarks."
-- ---------------------------------------------------------------------------
SELECT a.id, a.title, a.status,
       o.full_name AS author,
       (SELECT COUNT(*) FROM article_tags       WHERE article_id = a.id) AS tags,
       (SELECT COUNT(*) FROM article_categories WHERE article_id = a.id) AS categories,
       (SELECT COUNT(*) FROM article_related    WHERE article_id = a.id) AS related,
       (SELECT COUNT(*) FROM article_bookmarks  WHERE article_id = a.id) AS saved_by
FROM articles a
JOIN officers o ON o.id = a.author_officer_id
ORDER BY a.updated_at DESC;


-- ---------------------------------------------------------------------------
-- 11. The self-referencing relationship, shown as actual pairs.
--     Both sides of this join are the same table.
-- ---------------------------------------------------------------------------
SELECT src.title AS article, tgt.title AS points_to
FROM article_related r
JOIN articles src ON src.id = r.article_id
JOIN articles tgt ON tgt.id = r.related_article_id;


-- ---------------------------------------------------------------------------
-- 12. Student feedback (F3, US-12), joined to the ticket it evaluates.
-- ---------------------------------------------------------------------------
SELECT f.id, t.subject, s.full_name AS student,
       f.rating, f.comment, f.created_at
FROM feedback f
JOIN tickets  t ON t.id = f.ticket_id
JOIN students s ON s.id = f.student_id
ORDER BY f.created_at DESC;


-- ---------------------------------------------------------------------------
-- 13. Bookmarks filed into folders (F3) - a nullable folder_id, because a
--     bookmark that has not been filed anywhere is a legitimate state.
-- ---------------------------------------------------------------------------
SELECT s.full_name AS student,
       COALESCE(bf.name, '(not filed)') AS folder,
       t.subject AS bookmarked_ticket,
       b.created_at
FROM bookmarks b
JOIN students s ON s.id = b.student_id
JOIN tickets  t ON t.id = b.ticket_id
LEFT JOIN bookmark_folders bf ON bf.id = b.folder_id
ORDER BY s.full_name, folder;


-- ---------------------------------------------------------------------------
-- 14. Row counts across the whole system - a one-screen summary to close on.
-- ---------------------------------------------------------------------------
SELECT 'users' AS table_name, COUNT(*) AS rows_stored FROM users
UNION ALL SELECT 'students',          COUNT(*) FROM students
UNION ALL SELECT 'officers',          COUNT(*) FROM officers
UNION ALL SELECT 'administrators',    COUNT(*) FROM administrators
UNION ALL SELECT 'departments',       COUNT(*) FROM departments
UNION ALL SELECT 'categories',        COUNT(*) FROM categories
UNION ALL SELECT 'tickets',           COUNT(*) FROM tickets
UNION ALL SELECT 'attachments',       COUNT(*) FROM attachments
UNION ALL SELECT 'resolutions',       COUNT(*) FROM resolutions
UNION ALL SELECT 'staff_notes',       COUNT(*) FROM staff_notes
UNION ALL SELECT 'bookmarks',         COUNT(*) FROM bookmarks
UNION ALL SELECT 'bookmark_folders',  COUNT(*) FROM bookmark_folders
UNION ALL SELECT 'feedback',          COUNT(*) FROM feedback
UNION ALL SELECT 'archived_tickets',  COUNT(*) FROM archived_tickets
UNION ALL SELECT 'articles',          COUNT(*) FROM articles
UNION ALL SELECT 'announcements',     COUNT(*) FROM announcements
UNION ALL SELECT 'activity_log',      COUNT(*) FROM activity_log;
