-- Migration: users.deleted_at, for account removal (separate from suspension)
-- Date: 2026-09-26   Target: MySQL 8
--
--   Suspended: active = 0, deleted_at IS NULL      - temporary, can be undone
--   Removed:   active = 0, deleted_at IS NOT NULL  - final; the row stays so old
--              tickets and feedback still show a name
--
-- Run order: after 2026-09-23. The column is nullable, so old code keeps working.
-- Safe to re-run: each step checks information_schema first.
--
-- 1. Add the column. DATETIME rather than TIMESTAMP, which ends in 2038 and can
-- auto-update on every row change.

SET @has_deleted_at := (SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name   = 'users'
                          AND column_name  = 'deleted_at');

SET @sql := IF(@has_deleted_at = 0,
    'ALTER TABLE users ADD COLUMN deleted_at DATETIME NULL',
    'SELECT ''step 1: users.deleted_at already present'' AS step_1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 2. Index for the admin Users page, which filters out removed accounts on every load.
-- Guarded on its own so a re-run still adds it if step 1 already worked.
--
-- 3. No backfill on purpose: existing suspended accounts were suspended, not removed,
-- and we don't want to invent removal dates.
SET @has_index := (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name   = 'users'
                     AND index_name   = 'idx_users_deleted_at');

SET @sql := IF(@has_index = 0,
    'CREATE INDEX idx_users_deleted_at ON users (deleted_at)',
    'SELECT ''step 2: idx_users_deleted_at already present'' AS step_2');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- 4. Verify.
-- 4a. The column exists, is nullable and is a DATETIME.
SELECT column_name, data_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name   = 'users'
   AND column_name  = 'deleted_at';


-- 4b. The index exists.
SELECT index_name, column_name
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND table_name   = 'users'
   AND index_name   = 'idx_users_deleted_at';


-- 4c. removed_accounts must be 0 on the first run (suspended ones may be non-zero).
SELECT COUNT(*)                                                AS total_accounts,
       SUM(CASE WHEN active = 0 AND deleted_at IS NULL  THEN 1 ELSE 0 END) AS suspended_accounts,
       SUM(CASE WHEN deleted_at IS NOT NULL             THEN 1 ELSE 0 END) AS removed_accounts
  FROM users;


-- 4d. Nothing may be removed and still active. Must return 0 rows.
SELECT id, email, active, deleted_at
  FROM users
 WHERE deleted_at IS NOT NULL
   AND active = 1;
