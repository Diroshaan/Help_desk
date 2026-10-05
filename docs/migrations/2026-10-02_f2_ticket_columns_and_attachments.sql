-- Migration: ticket and attachment column fixes for ticket submission
-- Date: 2026-10-02   Author: Chamikara A. K (IT25102416)   Target: MySQL 8
--
-- Fixes columns that ddl-auto=update can't change: attachments.data (TINYBLOB ->
-- MEDIUMBLOB), tickets.status/priority (native ENUM -> VARCHAR(20)), subject/category
-- lengths. Adds tickets.version, attachments.uploaded_by_user_id and kind, and the
-- changed_by foreign key on ticket_status_changes.
--
-- Run order: after 2026-10-01. Start the app once on the mysql profile first (so
-- Hibernate creates ticket_status_changes), stop it, then run this whole script.
-- Safe to re-run: each step checks information_schema first. Not needed on H2.
--
-- 1. attachments.data -> MEDIUMBLOB (16MB). Running it twice does no harm, so no guard.

ALTER TABLE `attachments` MODIFY COLUMN `data` MEDIUMBLOB NOT NULL;
SELECT 'attachments.data: set to MEDIUMBLOB' AS result;


-- 2. status and priority: native ENUM -> VARCHAR(20), so new enum values don't need
-- a migration. Only runs while the column is still an ENUM.
SET @is_enum := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'tickets'
                   AND column_name = 'status' AND data_type = 'enum');
SET @sql := IF(@is_enum > 0,
    'ALTER TABLE `tickets` MODIFY COLUMN `status` VARCHAR(20) NOT NULL',
    'SELECT ''tickets.status: already not an ENUM, nothing to do'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@is_enum > 0, 'SELECT ''tickets.status: converted to VARCHAR(20)'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @is_enum := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'tickets'
                   AND column_name = 'priority' AND data_type = 'enum');
SET @sql := IF(@is_enum > 0,
    'ALTER TABLE `tickets` MODIFY COLUMN `priority` VARCHAR(20) NOT NULL',
    'SELECT ''tickets.priority: already not an ENUM, nothing to do'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@is_enum > 0, 'SELECT ''tickets.priority: converted to VARCHAR(20)'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 3. tickets.subject -> VARCHAR(150), only if every existing value fits.
-- Nothing is truncated; a value that is too long is reported instead.
SET @current_type := (SELECT column_type FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'subject');
SET @max_len := (SELECT COALESCE(MAX(CHAR_LENGTH(subject)), 0) FROM `tickets`);
SET @sql := IF(@current_type = 'varchar(150)',
    'SELECT ''tickets.subject: already varchar(150)'' AS result',
    IF(@max_len > 150,
       CONCAT('SELECT ''tickets.subject: NOT modified - longest existing value is ', @max_len,
              ' characters, see section 7'' AS result'),
       'ALTER TABLE `tickets` MODIFY COLUMN `subject` VARCHAR(150) NOT NULL'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@current_type <> 'varchar(150)' AND @max_len <= 150,
    'SELECT ''tickets.subject: set to VARCHAR(150)'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 4. tickets.category -> VARCHAR(120), same check. Left alone if fk_tickets_category
-- already exists.
SET @current_type := (SELECT column_type FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'category');
SET @max_len := (SELECT COALESCE(MAX(CHAR_LENGTH(category)), 0) FROM `tickets`);
SET @cat_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'tickets'
                  AND constraint_name = 'fk_tickets_category' AND constraint_type = 'FOREIGN KEY');
SET @sql := IF(@current_type = 'varchar(120)',
    'SELECT ''tickets.category: already varchar(120)'' AS result',
    IF(@cat_fk > 0,
       'SELECT ''tickets.category: left as it is - fk_tickets_category already limits it to categories.name'' AS result',
    IF(@max_len > 120,
       CONCAT('SELECT ''tickets.category: NOT modified - longest existing value is ', @max_len,
              ' characters, see section 7'' AS result'),
       'ALTER TABLE `tickets` MODIFY COLUMN `category` VARCHAR(120) NOT NULL')));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@current_type <> 'varchar(120)' AND @cat_fk = 0 AND @max_len <= 120,
    'SELECT ''tickets.category: set to VARCHAR(120)'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 5. tickets.version for optimistic locking. The DEFAULT fills existing rows with 0;
-- the UPDATE is just a backstop.
SET @has_version := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'version');
SET @sql := IF(@has_version = 0,
    'ALTER TABLE `tickets` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0',
    'SELECT ''tickets.version: already present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_version = 0, 'SELECT ''tickets.version: added, existing rows set to 0'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE `tickets` SET `version` = 0 WHERE `version` IS NULL;


-- 6. attachments.uploaded_by_user_id: add nullable, backfill, then make NOT NULL.
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'attachments'
                   AND column_name = 'uploaded_by_user_id');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE `attachments` ADD COLUMN `uploaded_by_user_id` BIGINT NULL',
    'SELECT ''attachments.uploaded_by_user_id: column already present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- Old attachments were all uploaded by the ticket's student. Also fixes 0, which
-- Hibernate leaves if it added the column first.
UPDATE `attachments` a
  JOIN `tickets` t ON t.`id` = a.`ticket_id`
   SET a.`uploaded_by_user_id` = t.`student_id`
 WHERE a.`uploaded_by_user_id` IS NULL OR a.`uploaded_by_user_id` = 0;

SET @is_nullable := (SELECT is_nullable FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'attachments'
                       AND column_name = 'uploaded_by_user_id');
SET @sql := IF(@is_nullable = 'YES',
    'ALTER TABLE `attachments` MODIFY COLUMN `uploaded_by_user_id` BIGINT NOT NULL',
    'SELECT ''attachments.uploaded_by_user_id: already NOT NULL'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- Foreign key to users, same pattern as 2026-10-01.
SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'attachments'
                  AND constraint_name = 'fk_attachments_uploaded_by' AND constraint_type = 'FOREIGN KEY');
SET @orphans := (SELECT COUNT(*) FROM `attachments` c
                 WHERE c.`uploaded_by_user_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `users` p WHERE p.`id` = c.`uploaded_by_user_id`));
SET @sql := IF(@has_fk > 0,
    'SELECT ''fk_attachments_uploaded_by: already present'' AS result',
    IF(@orphans > 0,
       CONCAT('SELECT ''fk_attachments_uploaded_by: NOT added - ', @orphans,
              ' orphan row(s) in attachments, see section 7'' AS result'),
       'ALTER TABLE `attachments` ADD CONSTRAINT `fk_attachments_uploaded_by` FOREIGN KEY (`uploaded_by_user_id`) REFERENCES `users` (`id`)'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_fk = 0 AND @orphans = 0, 'SELECT ''fk_attachments_uploaded_by: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 7. attachments.kind. The default marks every existing row as SUBMISSION, which is
-- correct - nothing wrote RESOLUTION before this.
SET @has_kind := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'attachments' AND column_name = 'kind');
SET @sql := IF(@has_kind = 0,
    'ALTER TABLE `attachments` ADD COLUMN `kind` VARCHAR(20) NOT NULL DEFAULT ''SUBMISSION''',
    'SELECT ''attachments.kind: already present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_kind = 0,
    'SELECT ''attachments.kind: added, existing rows set to SUBMISSION'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 8. ticket_status_changes.changed_by_user_id -> users. Hibernate creates the table and
-- its ticket key, but changed_by is a plain Long so only this script adds its key.
-- Skipped if the table doesn't exist yet.
SET @has_table := (SELECT COUNT(*) FROM information_schema.tables
                   WHERE table_schema = DATABASE() AND table_name = 'ticket_status_changes');
SET @sql := IF(@has_table = 0,
    'SELECT ''ticket_status_changes: table not found yet - start the application once on this profile, then re-run this script'' AS result',
    'SELECT ''ticket_status_changes: table present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'ticket_status_changes'
                  AND constraint_name = 'fk_status_changes_changed_by' AND constraint_type = 'FOREIGN KEY');


-- changed_by can be null (unknown officer), so only non-null values count as orphans.
SET @sql := IF(@has_table = 0 OR @has_fk > 0,
    'SELECT 0 INTO @orphans',
    'SELECT COUNT(*) INTO @orphans FROM `ticket_status_changes` c WHERE c.`changed_by_user_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `users` p WHERE p.`id` = c.`changed_by_user_id`)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(@has_table = 0,
    'SELECT ''fk_status_changes_changed_by: skipped - table not found yet'' AS result',
    IF(@has_fk > 0,
       'SELECT ''fk_status_changes_changed_by: already present'' AS result',
       IF(@orphans > 0,
          CONCAT('SELECT ''fk_status_changes_changed_by: NOT added - ', @orphans,
                 ' orphan row(s), see section 7'' AS result'),
          'ALTER TABLE `ticket_status_changes` ADD CONSTRAINT `fk_status_changes_changed_by` FOREIGN KEY (`changed_by_user_id`) REFERENCES `users` (`id`)')));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_table > 0 AND @has_fk = 0 AND @orphans = 0,
    'SELECT ''fk_status_changes_changed_by: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 9. Verify.
-- 9a. status/priority varchar(20), subject varchar(150), category varchar(120),
-- version bigint NOT NULL default 0.
SELECT column_name, column_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'tickets'
   AND column_name IN ('status', 'priority', 'subject', 'category', 'version')
 ORDER BY ordinal_position;


-- 9b. data mediumblob; uploaded_by_user_id bigint NOT NULL; kind varchar(20) default 'SUBMISSION'.
SELECT column_name, column_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'attachments'
   AND column_name IN ('data', 'uploaded_by_user_id', 'kind')
 ORDER BY ordinal_position;


-- 9c. Expect 3 rows.
SELECT tc.table_name, tc.constraint_name, kcu.column_name,
       kcu.referenced_table_name, kcu.referenced_column_name
  FROM information_schema.table_constraints tc
  JOIN information_schema.key_column_usage kcu
    ON kcu.constraint_schema = tc.constraint_schema
   AND kcu.table_name        = tc.table_name
   AND kcu.constraint_name   = tc.constraint_name
 WHERE tc.constraint_schema = DATABASE()
   AND tc.constraint_type   = 'FOREIGN KEY'
   AND tc.constraint_name IN ('fk_attachments_uploaded_by',
        'fk_status_changes_changed_by',
        'fk_status_changes_ticket')
 ORDER BY tc.table_name, tc.constraint_name;


-- 9d. All must be 0. If not, re-read that step's result row.
SELECT
    (SELECT COUNT(*) FROM `tickets` WHERE `version` IS NULL)               AS tickets_null_version,
    (SELECT COUNT(*) FROM `attachments` WHERE `uploaded_by_user_id` IS NULL) AS attachments_null_uploader,
    (SELECT COUNT(*) FROM `attachments` WHERE `kind` IS NULL)               AS attachments_null_kind,
    (SELECT COUNT(*) FROM `tickets` WHERE CHAR_LENGTH(`subject`) > 150)     AS tickets_subject_too_long,
    (SELECT COUNT(*) FROM `tickets` WHERE CHAR_LENGTH(`category`) > 120)    AS tickets_category_too_long;
