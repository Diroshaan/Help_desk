-- ===========================================================================
--  F4 - Ticket Resolution & Queue Engine: resolution file column, officer
--  supervisor, and routing of existing tickets (Vimansa, IT25101250)
--  Branch: feat/f4-final-backend
--  Supports: F4-N6 (MEDIUMBLOB), #46 (supervisor), F4-N5 (routing)
-- ===========================================================================
--
--  1. resolutions.attachment_data was a bare @Lob, which MySQL resolves to
--     TINYBLOB (255 bytes). The entity now says MEDIUMBLOB.
--  2. officers.supervisor_id (BIGINT NULL) and fk_officer_supervisor ->
--     officers(id): each officer has at most one supervisor.
--  3. Tickets that arrived before routing existed have no department. The
--     OPEN ones are routed by category now.
--
--  State-aware (information_schema first, then PREPARE/EXECUTE) and safe to
--  run more than once, the same pattern as the earlier migrations. Run in
--  DBeaver (Alt+X) on defaultdb after starting the app once on the mysql
--  profile, as for the F2 migration.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- 1. resolutions.attachment_data -> MEDIUMBLOB (only if it is not already).
-- ---------------------------------------------------------------------------
SET @col_type := (SELECT DATA_TYPE FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'resolutions'
                    AND column_name = 'attachment_data');
SET @sql := IF(@col_type IS NOT NULL AND @col_type <> 'mediumblob',
    'ALTER TABLE `resolutions` MODIFY COLUMN `attachment_data` MEDIUMBLOB NULL',
    'SELECT ''resolutions.attachment_data: already MEDIUMBLOB (or table missing), nothing to do'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- ---------------------------------------------------------------------------
-- 2. officers.supervisor_id and its foreign key.
-- ---------------------------------------------------------------------------
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


-- ---------------------------------------------------------------------------
-- 3. Route existing OPEN tickets by category.
--    routed by category on 2026-10-03; closed tickets left as they were.
-- ---------------------------------------------------------------------------
UPDATE tickets t
  JOIN categories c ON c.name = t.category
   SET t.assigned_department_id = c.department_code
 WHERE t.assigned_department_id IS NULL
   AND t.status = 'OPEN';


-- ---------------------------------------------------------------------------
-- 4. Verification.
-- ---------------------------------------------------------------------------
SELECT column_name, data_type FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'resolutions' AND column_name = 'attachment_data';

SELECT column_name, is_nullable FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officers' AND column_name = 'supervisor_id';

SELECT constraint_name FROM information_schema.table_constraints
 WHERE table_schema = DATABASE() AND table_name = 'officers' AND constraint_name = 'fk_officer_supervisor';

SELECT status, COUNT(*) AS tickets, SUM(assigned_department_id IS NULL) AS still_unrouted
  FROM tickets GROUP BY status;
