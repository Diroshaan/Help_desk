-- Migration: resolution file column, officer supervisor, and routing of old tickets
-- Date: 2026-10-03   Author: Vimansa (IT25101250)   Target: MySQL 8
--
-- Run order: after 2026-10-02. Start the app once on the mysql profile first, then
-- run this whole script. Safe to re-run: each step checks information_schema first.
--
-- 1. resolutions.attachment_data -> MEDIUMBLOB (a bare @Lob is only 255 bytes on MySQL).

SET @col_type := (SELECT DATA_TYPE FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'resolutions'
                    AND column_name = 'attachment_data');
SET @sql := IF(@col_type IS NOT NULL AND @col_type <> 'mediumblob',
    'ALTER TABLE `resolutions` MODIFY COLUMN `attachment_data` MEDIUMBLOB NULL',
    'SELECT ''resolutions.attachment_data: already MEDIUMBLOB (or table missing), nothing to do'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 2. officers.supervisor_id: each officer has at most one supervisor.
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'officers'
                   AND column_name = 'supervisor_id');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE `officers` ADD COLUMN `supervisor_id` BIGINT NULL',
    'SELECT ''officers.supervisor_id: already present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'officers'
                  AND constraint_name = 'fk_officer_supervisor' AND constraint_type = 'FOREIGN KEY');
SET @sql := IF(@has_fk = 0,
    'ALTER TABLE `officers` ADD CONSTRAINT `fk_officer_supervisor` FOREIGN KEY (`supervisor_id`) REFERENCES `officers` (`id`)',
    'SELECT ''fk_officer_supervisor: already present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 3. Tickets created before routing have no department. Route the OPEN ones by
-- category; closed tickets stay as they are.
UPDATE tickets t
  JOIN categories c ON c.name = t.category
   SET t.assigned_department_id = c.department_code
 WHERE t.assigned_department_id IS NULL
   AND t.status = 'OPEN';


-- 4. Verify: mediumblob, supervisor_id nullable, the foreign key, and no unrouted
-- OPEN tickets.
SELECT column_name, data_type FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'resolutions' AND column_name = 'attachment_data';

SELECT column_name, is_nullable FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officers' AND column_name = 'supervisor_id';

SELECT constraint_name FROM information_schema.table_constraints
 WHERE table_schema = DATABASE() AND table_name = 'officers' AND constraint_name = 'fk_officer_supervisor';

SELECT status, COUNT(*) AS tickets, SUM(assigned_department_id IS NULL) AS still_unrouted
  FROM tickets GROUP BY status;
