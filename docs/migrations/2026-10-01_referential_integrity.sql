-- Migration: add 21 named foreign keys to the ticket-side tables
-- Date: 2026-10-01   Target: MySQL 8
--
-- These entities store parent ids as plain Long/String fields, so Hibernate makes
-- the columns but no constraints. This adds them so the database itself rejects,
-- for example, a ticket for a student that doesn't exist. No data or column changes.
-- ON DELETE is the default (RESTRICT): the app never hard-deletes a parent, and a
-- manual delete should fail rather than cascade away history.
--
-- Each key is only added if it isn't there yet and no existing row breaks it. If a
-- table has orphan rows that key is skipped and reported; nothing is deleted.
-- Safe to re-run. Run order: after 2026-09-26. Every result row should say
-- "added" or "already present". Not needed on H2.
--
-- 1. Add the foreign keys.
-- 1.1 fk_tickets_student: tickets.student_id -> students.id

SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'tickets'
                  AND constraint_name = 'fk_tickets_student' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `tickets` c
                 WHERE c.`student_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_tickets_student: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_tickets_student: NOT added - ', @orphans, ' orphan row(s) in tickets, see section 3'' AS result'),
       'ALTER TABLE `tickets` ADD CONSTRAINT `fk_tickets_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_tickets_student: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.2 fk_tickets_assigned_officer: tickets.assigned_officer_id -> officers.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'tickets'
                  AND constraint_name = 'fk_tickets_assigned_officer' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `tickets` c
                 WHERE c.`assigned_officer_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `officers` p WHERE p.`id` = c.`assigned_officer_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_tickets_assigned_officer: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_tickets_assigned_officer: NOT added - ', @orphans, ' orphan row(s) in tickets, see section 3'' AS result'),
       'ALTER TABLE `tickets` ADD CONSTRAINT `fk_tickets_assigned_officer` FOREIGN KEY (`assigned_officer_id`) REFERENCES `officers` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_tickets_assigned_officer: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.3 fk_tickets_assigned_department: tickets.assigned_department_id -> departments.code
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'tickets'
                  AND constraint_name = 'fk_tickets_assigned_department' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `tickets` c
                 WHERE c.`assigned_department_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `departments` p WHERE p.`code` = c.`assigned_department_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_tickets_assigned_department: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_tickets_assigned_department: NOT added - ', @orphans, ' orphan row(s) in tickets, see section 3'' AS result'),
       'ALTER TABLE `tickets` ADD CONSTRAINT `fk_tickets_assigned_department` FOREIGN KEY (`assigned_department_id`) REFERENCES `departments` (`code`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_tickets_assigned_department: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.4 fk_tickets_category: tickets.category -> categories.name
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'tickets'
                  AND constraint_name = 'fk_tickets_category' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `tickets` c
                 WHERE c.`category` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `categories` p WHERE p.`name` = c.`category`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_tickets_category: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_tickets_category: NOT added - ', @orphans, ' orphan row(s) in tickets, see section 3'' AS result'),
       'ALTER TABLE `tickets` ADD CONSTRAINT `fk_tickets_category` FOREIGN KEY (`category`) REFERENCES `categories` (`name`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_tickets_category: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.5 fk_attachments_ticket: attachments.ticket_id -> tickets.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'attachments'
                  AND constraint_name = 'fk_attachments_ticket' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `attachments` c
                 WHERE c.`ticket_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_attachments_ticket: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_attachments_ticket: NOT added - ', @orphans, ' orphan row(s) in attachments, see section 3'' AS result'),
       'ALTER TABLE `attachments` ADD CONSTRAINT `fk_attachments_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_attachments_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.6 fk_resolutions_ticket: resolutions.ticket_id -> tickets.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'resolutions'
                  AND constraint_name = 'fk_resolutions_ticket' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `resolutions` c
                 WHERE c.`ticket_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_resolutions_ticket: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_resolutions_ticket: NOT added - ', @orphans, ' orphan row(s) in resolutions, see section 3'' AS result'),
       'ALTER TABLE `resolutions` ADD CONSTRAINT `fk_resolutions_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_resolutions_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.7 fk_resolutions_officer: resolutions.officer_id -> officers.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'resolutions'
                  AND constraint_name = 'fk_resolutions_officer' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `resolutions` c
                 WHERE c.`officer_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `officers` p WHERE p.`id` = c.`officer_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_resolutions_officer: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_resolutions_officer: NOT added - ', @orphans, ' orphan row(s) in resolutions, see section 3'' AS result'),
       'ALTER TABLE `resolutions` ADD CONSTRAINT `fk_resolutions_officer` FOREIGN KEY (`officer_id`) REFERENCES `officers` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_resolutions_officer: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.8 fk_staff_notes_ticket: staff_notes.ticket_id -> tickets.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'staff_notes'
                  AND constraint_name = 'fk_staff_notes_ticket' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `staff_notes` c
                 WHERE c.`ticket_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_staff_notes_ticket: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_staff_notes_ticket: NOT added - ', @orphans, ' orphan row(s) in staff_notes, see section 3'' AS result'),
       'ALTER TABLE `staff_notes` ADD CONSTRAINT `fk_staff_notes_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_staff_notes_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.9 fk_staff_notes_officer: staff_notes.officer_id -> officers.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'staff_notes'
                  AND constraint_name = 'fk_staff_notes_officer' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `staff_notes` c
                 WHERE c.`officer_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `officers` p WHERE p.`id` = c.`officer_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_staff_notes_officer: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_staff_notes_officer: NOT added - ', @orphans, ' orphan row(s) in staff_notes, see section 3'' AS result'),
       'ALTER TABLE `staff_notes` ADD CONSTRAINT `fk_staff_notes_officer` FOREIGN KEY (`officer_id`) REFERENCES `officers` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_staff_notes_officer: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.10 fk_feedback_ticket: feedback.ticket_id -> tickets.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'feedback'
                  AND constraint_name = 'fk_feedback_ticket' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `feedback` c
                 WHERE c.`ticket_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_feedback_ticket: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_feedback_ticket: NOT added - ', @orphans, ' orphan row(s) in feedback, see section 3'' AS result'),
       'ALTER TABLE `feedback` ADD CONSTRAINT `fk_feedback_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_feedback_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.11 fk_feedback_student: feedback.student_id -> students.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'feedback'
                  AND constraint_name = 'fk_feedback_student' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `feedback` c
                 WHERE c.`student_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_feedback_student: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_feedback_student: NOT added - ', @orphans, ' orphan row(s) in feedback, see section 3'' AS result'),
       'ALTER TABLE `feedback` ADD CONSTRAINT `fk_feedback_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_feedback_student: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.12 fk_bookmarks_ticket: bookmarks.ticket_id -> tickets.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'bookmarks'
                  AND constraint_name = 'fk_bookmarks_ticket' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `bookmarks` c
                 WHERE c.`ticket_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_bookmarks_ticket: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_bookmarks_ticket: NOT added - ', @orphans, ' orphan row(s) in bookmarks, see section 3'' AS result'),
       'ALTER TABLE `bookmarks` ADD CONSTRAINT `fk_bookmarks_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_bookmarks_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.13 fk_bookmarks_student: bookmarks.student_id -> students.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'bookmarks'
                  AND constraint_name = 'fk_bookmarks_student' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `bookmarks` c
                 WHERE c.`student_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_bookmarks_student: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_bookmarks_student: NOT added - ', @orphans, ' orphan row(s) in bookmarks, see section 3'' AS result'),
       'ALTER TABLE `bookmarks` ADD CONSTRAINT `fk_bookmarks_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_bookmarks_student: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.14 fk_bookmarks_folder: bookmarks.folder_id -> bookmark_folders.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'bookmarks'
                  AND constraint_name = 'fk_bookmarks_folder' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `bookmarks` c
                 WHERE c.`folder_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `bookmark_folders` p WHERE p.`id` = c.`folder_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_bookmarks_folder: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_bookmarks_folder: NOT added - ', @orphans, ' orphan row(s) in bookmarks, see section 3'' AS result'),
       'ALTER TABLE `bookmarks` ADD CONSTRAINT `fk_bookmarks_folder` FOREIGN KEY (`folder_id`) REFERENCES `bookmark_folders` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_bookmarks_folder: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.15 fk_bookmark_folders_student: bookmark_folders.student_id -> students.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'bookmark_folders'
                  AND constraint_name = 'fk_bookmark_folders_student' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `bookmark_folders` c
                 WHERE c.`student_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_bookmark_folders_student: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_bookmark_folders_student: NOT added - ', @orphans, ' orphan row(s) in bookmark_folders, see section 3'' AS result'),
       'ALTER TABLE `bookmark_folders` ADD CONSTRAINT `fk_bookmark_folders_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_bookmark_folders_student: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.16 fk_archived_tickets_ticket: archived_tickets.ticket_id -> tickets.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'archived_tickets'
                  AND constraint_name = 'fk_archived_tickets_ticket' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `archived_tickets` c
                 WHERE c.`ticket_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_archived_tickets_ticket: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_archived_tickets_ticket: NOT added - ', @orphans, ' orphan row(s) in archived_tickets, see section 3'' AS result'),
       'ALTER TABLE `archived_tickets` ADD CONSTRAINT `fk_archived_tickets_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_archived_tickets_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.17 fk_archived_tickets_student: archived_tickets.student_id -> students.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'archived_tickets'
                  AND constraint_name = 'fk_archived_tickets_student' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `archived_tickets` c
                 WHERE c.`student_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_archived_tickets_student: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_archived_tickets_student: NOT added - ', @orphans, ' orphan row(s) in archived_tickets, see section 3'' AS result'),
       'ALTER TABLE `archived_tickets` ADD CONSTRAINT `fk_archived_tickets_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_archived_tickets_student: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.18 fk_article_bookmarks_article: article_bookmarks.article_id -> articles.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'article_bookmarks'
                  AND constraint_name = 'fk_article_bookmarks_article' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `article_bookmarks` c
                 WHERE c.`article_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `articles` p WHERE p.`id` = c.`article_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_article_bookmarks_article: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_article_bookmarks_article: NOT added - ', @orphans, ' orphan row(s) in article_bookmarks, see section 3'' AS result'),
       'ALTER TABLE `article_bookmarks` ADD CONSTRAINT `fk_article_bookmarks_article` FOREIGN KEY (`article_id`) REFERENCES `articles` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_article_bookmarks_article: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.19 fk_article_bookmarks_student: article_bookmarks.student_id -> students.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'article_bookmarks'
                  AND constraint_name = 'fk_article_bookmarks_student' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `article_bookmarks` c
                 WHERE c.`student_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_article_bookmarks_student: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_article_bookmarks_student: NOT added - ', @orphans, ' orphan row(s) in article_bookmarks, see section 3'' AS result'),
       'ALTER TABLE `article_bookmarks` ADD CONSTRAINT `fk_article_bookmarks_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_article_bookmarks_student: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.20 fk_activity_log_student: activity_log.student_id -> students.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'activity_log'
                  AND constraint_name = 'fk_activity_log_student' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `activity_log` c
                 WHERE c.`student_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_activity_log_student: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_activity_log_student: NOT added - ', @orphans, ' orphan row(s) in activity_log, see section 3'' AS result'),
       'ALTER TABLE `activity_log` ADD CONSTRAINT `fk_activity_log_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_activity_log_student: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 1.21 fk_notifications_recipient: notifications.recipient_user_id -> users.id
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'notifications'
                  AND constraint_name = 'fk_notifications_recipient' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `notifications` c
                 WHERE c.`recipient_user_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `users` p WHERE p.`id` = c.`recipient_user_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_notifications_recipient: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_notifications_recipient: NOT added - ', @orphans, ' orphan row(s) in notifications, see section 3'' AS result'),
       'ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_recipient` FOREIGN KEY (`recipient_user_id`) REFERENCES `users` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_notifications_recipient: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 2. Verify.
-- 2a. Expect 21 rows. Fewer means some were skipped - see section 3.
SELECT tc.table_name, tc.constraint_name, kcu.column_name,
       kcu.referenced_table_name, kcu.referenced_column_name
  FROM information_schema.table_constraints tc
  JOIN information_schema.key_column_usage kcu
    ON kcu.constraint_schema = tc.constraint_schema
   AND kcu.table_name        = tc.table_name
   AND kcu.constraint_name   = tc.constraint_name
 WHERE tc.constraint_schema = DATABASE()
   AND tc.constraint_type   = 'FOREIGN KEY'
   AND tc.constraint_name IN ('fk_tickets_student',
        'fk_tickets_assigned_officer',
        'fk_tickets_assigned_department',
        'fk_tickets_category',
        'fk_attachments_ticket',
        'fk_resolutions_ticket',
        'fk_resolutions_officer',
        'fk_staff_notes_ticket',
        'fk_staff_notes_officer',
        'fk_feedback_ticket',
        'fk_feedback_student',
        'fk_bookmarks_ticket',
        'fk_bookmarks_student',
        'fk_bookmarks_folder',
        'fk_bookmark_folders_student',
        'fk_archived_tickets_ticket',
        'fk_archived_tickets_student',
        'fk_article_bookmarks_article',
        'fk_article_bookmarks_student',
        'fk_activity_log_student',
        'fk_notifications_recipient')
 ORDER BY tc.table_name, tc.constraint_name;


-- 2b. Every foreign key in the schema.
SELECT table_name, constraint_name
  FROM information_schema.table_constraints
 WHERE constraint_schema = DATABASE() AND constraint_type = 'FOREIGN KEY'
 ORDER BY table_name, constraint_name;


-- 3. Orphan report: rows pointing at a parent that doesn't exist. Every count should
-- be 0. Look at any non-zero rows before deciding what to do; don't just delete them.
SELECT 'fk_tickets_student' AS constraint_name, COUNT(*) AS orphans FROM `tickets` c WHERE c.`student_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`)
UNION ALL
SELECT 'fk_tickets_assigned_officer' AS constraint_name, COUNT(*) AS orphans FROM `tickets` c WHERE c.`assigned_officer_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `officers` p WHERE p.`id` = c.`assigned_officer_id`)
UNION ALL
SELECT 'fk_tickets_assigned_department' AS constraint_name, COUNT(*) AS orphans FROM `tickets` c WHERE c.`assigned_department_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `departments` p WHERE p.`code` = c.`assigned_department_id`)
UNION ALL
SELECT 'fk_tickets_category' AS constraint_name, COUNT(*) AS orphans FROM `tickets` c WHERE c.`category` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `categories` p WHERE p.`name` = c.`category`)
UNION ALL
SELECT 'fk_attachments_ticket' AS constraint_name, COUNT(*) AS orphans FROM `attachments` c WHERE c.`ticket_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`)
UNION ALL
SELECT 'fk_resolutions_ticket' AS constraint_name, COUNT(*) AS orphans FROM `resolutions` c WHERE c.`ticket_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`)
UNION ALL
SELECT 'fk_resolutions_officer' AS constraint_name, COUNT(*) AS orphans FROM `resolutions` c WHERE c.`officer_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `officers` p WHERE p.`id` = c.`officer_id`)
UNION ALL
SELECT 'fk_staff_notes_ticket' AS constraint_name, COUNT(*) AS orphans FROM `staff_notes` c WHERE c.`ticket_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`)
UNION ALL
SELECT 'fk_staff_notes_officer' AS constraint_name, COUNT(*) AS orphans FROM `staff_notes` c WHERE c.`officer_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `officers` p WHERE p.`id` = c.`officer_id`)
UNION ALL
SELECT 'fk_feedback_ticket' AS constraint_name, COUNT(*) AS orphans FROM `feedback` c WHERE c.`ticket_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`)
UNION ALL
SELECT 'fk_feedback_student' AS constraint_name, COUNT(*) AS orphans FROM `feedback` c WHERE c.`student_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`)
UNION ALL
SELECT 'fk_bookmarks_ticket' AS constraint_name, COUNT(*) AS orphans FROM `bookmarks` c WHERE c.`ticket_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`)
UNION ALL
SELECT 'fk_bookmarks_student' AS constraint_name, COUNT(*) AS orphans FROM `bookmarks` c WHERE c.`student_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`)
UNION ALL
SELECT 'fk_bookmarks_folder' AS constraint_name, COUNT(*) AS orphans FROM `bookmarks` c WHERE c.`folder_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `bookmark_folders` p WHERE p.`id` = c.`folder_id`)
UNION ALL
SELECT 'fk_bookmark_folders_student' AS constraint_name, COUNT(*) AS orphans FROM `bookmark_folders` c WHERE c.`student_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`)
UNION ALL
SELECT 'fk_archived_tickets_ticket' AS constraint_name, COUNT(*) AS orphans FROM `archived_tickets` c WHERE c.`ticket_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `tickets` p WHERE p.`id` = c.`ticket_id`)
UNION ALL
SELECT 'fk_archived_tickets_student' AS constraint_name, COUNT(*) AS orphans FROM `archived_tickets` c WHERE c.`student_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`)
UNION ALL
SELECT 'fk_article_bookmarks_article' AS constraint_name, COUNT(*) AS orphans FROM `article_bookmarks` c WHERE c.`article_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `articles` p WHERE p.`id` = c.`article_id`)
UNION ALL
SELECT 'fk_article_bookmarks_student' AS constraint_name, COUNT(*) AS orphans FROM `article_bookmarks` c WHERE c.`student_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`)
UNION ALL
SELECT 'fk_activity_log_student' AS constraint_name, COUNT(*) AS orphans FROM `activity_log` c WHERE c.`student_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `students` p WHERE p.`id` = c.`student_id`)
UNION ALL
SELECT 'fk_notifications_recipient' AS constraint_name, COUNT(*) AS orphans FROM `notifications` c WHERE c.`recipient_user_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `users` p WHERE p.`id` = c.`recipient_user_id`);
