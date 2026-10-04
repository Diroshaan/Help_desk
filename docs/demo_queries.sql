-- UNIHELP - database demo queries (MySQL)
-- Read-only: only SELECT and DESCRIBE, so it is safe to run any number of times,
-- in any order, after the migrations. Run one query at a time (click inside it,
-- then Ctrl+Enter) rather than the whole file.
--
-- 1. How many tables the system has (read from the server, not the IDE cache).

SELECT COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = DATABASE();


-- 2. List them.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY table_name;


-- 3. The user hierarchy (JOINED inheritance). Shared columns are in users, each
-- subtype has its own table with the same id. There is no stored role column -
-- the role comes from which subtype table the row is in.
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


-- 4. A subtype row can't exist without its users row. Expect 0 rows.
SELECT o.id AS orphaned_officer
FROM officers o
LEFT JOIN users u ON u.id = o.id
WHERE u.id IS NULL;


-- 5. Every foreign key, by name.
SELECT constraint_name, table_name, column_name, referenced_table_name
FROM information_schema.key_column_usage
WHERE constraint_schema = DATABASE()
  AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name;


-- 6. Every UNIQUE constraint, where the database enforces a rule (for example one
-- resolution per ticket), which still holds if two requests arrive at once.
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


-- 7. Categories and the department each one belongs to.
SELECT d.code AS dept_code, d.name AS department, c.id AS category_id, c.name AS category
FROM categories c
JOIN departments d ON d.code = c.department_code
ORDER BY d.name, c.name;


-- 8. Latest tickets with the student who raised them.
SELECT t.id, t.subject, t.category, t.priority, t.status,
       s.full_name AS raised_by, t.created_at
FROM tickets t
JOIN students s ON s.id = t.student_id
ORDER BY t.created_at DESC
LIMIT 20;


-- 9. Resolution time is derived from two timestamps, not stored as its own column.
SELECT t.id, t.subject, t.created_at, t.resolved_at,
       TIMESTAMPDIFF(HOUR, t.created_at, t.resolved_at) AS resolution_hours
FROM tickets t
WHERE t.resolved_at IS NOT NULL
ORDER BY t.resolved_at DESC;


-- 10. Knowledge base: tags (multivalued attribute), categories (many-to-many),
-- related articles (self-referencing many-to-many) and student bookmarks.
SELECT a.id, a.title, a.status,
       o.full_name AS author,
       (SELECT COUNT(*) FROM article_tags       WHERE article_id = a.id) AS tags,
       (SELECT COUNT(*) FROM article_categories WHERE article_id = a.id) AS categories,
       (SELECT COUNT(*) FROM article_related    WHERE article_id = a.id) AS related,
       (SELECT COUNT(*) FROM article_bookmarks  WHERE article_id = a.id) AS saved_by
FROM articles a
JOIN officers o ON o.id = a.author_officer_id
ORDER BY a.updated_at DESC;


-- 11. Related-article pairs. Both sides of the join are the articles table.
SELECT src.title AS article, tgt.title AS points_to
FROM article_related r
JOIN articles src ON src.id = r.article_id
JOIN articles tgt ON tgt.id = r.related_article_id;


-- 12. Student feedback with the ticket it is about.
SELECT f.id, t.subject, s.full_name AS student,
       f.rating, f.comment, f.created_at
FROM feedback f
JOIN tickets  t ON t.id = f.ticket_id
JOIN students s ON s.id = f.student_id
ORDER BY f.created_at DESC;


-- 13. Bookmarks and their folders. folder_id can be null (not filed yet).
SELECT s.full_name AS student,
       COALESCE(bf.name, '(not filed)') AS folder,
       t.subject AS bookmarked_ticket,
       b.created_at
FROM bookmarks b
JOIN students s ON s.id = b.student_id
JOIN tickets  t ON t.id = b.ticket_id
LEFT JOIN bookmark_folders bf ON bf.id = b.folder_id
ORDER BY s.full_name, folder;


-- 14. Row counts across the system.
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
