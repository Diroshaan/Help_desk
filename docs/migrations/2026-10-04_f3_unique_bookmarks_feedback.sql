-- Migration: one bookmark per student per ticket, and one feedback per ticket
-- Date: 2026-10-04   Author: Amarasinghe S. D (IT25103424)   Target: MySQL 8
--
-- The services check existsBy... before saving, but two clicks at the same moment
-- can both pass that check. Only a UNIQUE constraint stops the second insert.
--
-- Run order: last; run it BEFORE starting the app on the mysql profile, because
-- Hibernate would try to add the same constraints and fail while duplicates exist.
-- Safe to re-run. Not needed on H2.
--
-- 1. Report how many duplicate rows there are.

SELECT 'bookmarks: duplicate rows to delete' AS check_name, COUNT(*) AS duplicate_rows
FROM `bookmarks` b1
WHERE EXISTS (SELECT 1 FROM `bookmarks` b2
              WHERE b2.`student_id` = b1.`student_id`
                AND b2.`ticket_id` = b1.`ticket_id`
                AND b2.`id` < b1.`id`)
UNION ALL
SELECT 'feedback: duplicate rows to delete' AS check_name, COUNT(*) AS duplicate_rows
FROM `feedback` f1
WHERE EXISTS (SELECT 1 FROM `feedback` f2
              WHERE f2.`ticket_id` = f1.`ticket_id`
                AND f2.`id` < f1.`id`);


-- 2. Delete duplicates, keeping the lowest id (the first one made). Deleting is fine
-- here: a duplicate is the same row twice, and nothing else points at these tables.
DELETE b1 FROM `bookmarks` b1
JOIN `bookmarks` b2
  ON b1.`student_id` = b2.`student_id`
 AND b1.`ticket_id` = b2.`ticket_id`
 AND b1.`id` > b2.`id`;

DELETE f1 FROM `feedback` f1
JOIN `feedback` f2
  ON f1.`ticket_id` = f2.`ticket_id`
 AND f1.`id` > f2.`id`;


-- 3. Add the constraints, skipping any that already exist.
-- 3.1 uq_bookmark_student_ticket
SET @has_uq := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'bookmarks'
                  AND constraint_name = 'uq_bookmark_student_ticket' AND constraint_type = 'UNIQUE');
SET @sql := IF(@has_uq > 0,
    'SELECT ''uq_bookmark_student_ticket: already present'' AS result',
    'ALTER TABLE `bookmarks` ADD CONSTRAINT `uq_bookmark_student_ticket` UNIQUE (`student_id`, `ticket_id`)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_uq = 0, 'SELECT ''uq_bookmark_student_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 3.2 uq_feedback_ticket
SET @has_uq := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'feedback'
                  AND constraint_name = 'uq_feedback_ticket' AND constraint_type = 'UNIQUE');
SET @sql := IF(@has_uq > 0,
    'SELECT ''uq_feedback_ticket: already present'' AS result',
    'ALTER TABLE `feedback` ADD CONSTRAINT `uq_feedback_ticket` UNIQUE (`ticket_id`)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_uq = 0, 'SELECT ''uq_feedback_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 4. Verify: both constraints listed and 0 duplicates left.
SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN ('uq_bookmark_student_ticket', 'uq_feedback_ticket')
ORDER BY table_name;

SELECT 'bookmarks: duplicates remaining' AS check_name, COUNT(*) AS duplicate_rows
FROM (SELECT `student_id`, `ticket_id` FROM `bookmarks`
      GROUP BY `student_id`, `ticket_id` HAVING COUNT(*) > 1) d
UNION ALL
SELECT 'feedback: duplicates remaining' AS check_name, COUNT(*) AS duplicate_rows
FROM (SELECT `ticket_id` FROM `feedback`
      GROUP BY `ticket_id` HAVING COUNT(*) > 1) d;
