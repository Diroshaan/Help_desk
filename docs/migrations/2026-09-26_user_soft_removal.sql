-- ===========================================================================
--  Shared user model - users.deleted_at (account removal, distinct from
--  suspension)
--  Branch: feat/shared-user-soft-removal
--  Supports: F6 #48 ("Remove account" and what DELETE means)
-- ===========================================================================
--
--  WHAT THIS ADDS
--
--    users.deleted_at  DATETIME NULL
--
--  NULL            = the account was never removed (every existing row)
--  a timestamp     = an administrator removed the account, at that moment
--
--  Suspension is unchanged and is a different state:
--
--    SUSPENDED  active = 0, deleted_at IS NULL     temporary, reversible
--    REMOVED    active = 0, deleted_at IS NOT NULL final; the row survives so
--                                                  tickets, bookmarks, feedback
--                                                  and log rows still resolve
--                                                  to a name
--
--  ORDER (Team guide 5.3)
--
--    1. Test on H2 (no profile). H2 rebuilds from the entities on every start
--       and needs none of this.
--    2. Merge the pull request into develop.
--    3. Tell the group to stop running against Aiven.
--    4. Run this script on Aiven.
--    5. Start the new code once on the mysql profile and check it works.
--    6. Tell the group to pull develop.
--
--  This column is ADDITIVE and NULLABLE, so unlike
--  2026-09-23_f1_names_contacts_officer_prefs.sql it does not break the old
--  code: a build that predates it simply never reads or writes the column.
--  Running the new code first is also safe - Hibernate (ddl-auto=update) adds
--  a nullable column by itself, and step 1 below then finds it already there
--  and does nothing. The script exists so the shared database is changed
--  deliberately and identically everywhere, not as a side effect of whoever
--  happened to start the application first.
--
--  Safe to run more than once: every step checks information_schema before it
--  alters anything, the same pattern as the 23 September script.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- 1. ADD users.deleted_at
--
--    DATETIME, not TIMESTAMP. MySQL's TIMESTAMP range ends in 2038 and, on
--    older server settings, a TIMESTAMP column can be silently auto-updated on
--    every UPDATE of the row - which would rewrite the removal date every time
--    anything else about the account changed. DATETIME does neither.
--
--    NULL is the default and is never given one explicitly: "not removed" is
--    the absence of a date, not a sentinel value.
-- ---------------------------------------------------------------------------
SET @has_deleted_at := (SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name   = 'users'
                          AND column_name  = 'deleted_at');

SET @sql := IF(@has_deleted_at = 0,
    'ALTER TABLE users ADD COLUMN deleted_at DATETIME NULL',
    'SELECT ''step 1: users.deleted_at already present'' AS step_1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- ---------------------------------------------------------------------------
-- 2. INDEX for the account listing
--
--    The admin Users page will ask for "accounts that were not removed" on
--    every load. Without an index that is a full scan of the users table, and
--    the users table is one of the two that grow for as long as the system is
--    used.
--
--    Created separately from the column, and guarded separately, because a
--    re-run after step 1 succeeded but step 2 failed must still add the index.
-- ---------------------------------------------------------------------------
SET @has_index := (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name   = 'users'
                     AND index_name   = 'idx_users_deleted_at');

SET @sql := IF(@has_index = 0,
    'CREATE INDEX idx_users_deleted_at ON users (deleted_at)',
    'SELECT ''step 2: idx_users_deleted_at already present'' AS step_2');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- ---------------------------------------------------------------------------
-- 3. NO BACKFILL - deliberately
--
--    There are suspended accounts on Aiven today (officer.demo@helpdesk.local
--    among them). It is tempting to mark the long-suspended ones as removed so
--    the new screen looks tidy.
--
--    Don't. Nobody recorded why those accounts were suspended or when, so any
--    deleted_at written here would be an invented audit fact that looks exactly
--    like a real one. Every existing row keeps NULL, which is true: we do not
--    know that they were removed, because they weren't - they were suspended.
--
--    An administrator can remove them through the screen afterwards, and that
--    click produces a real timestamp.
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- 4. VERIFICATION - run these and read the output
-- ---------------------------------------------------------------------------

-- 4a. The column exists, is nullable, and is a DATETIME.
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

-- 4c. Nothing was backfilled: removed_accounts must be 0 on the first run.
--     suspended_accounts may be non-zero, and those are suspensions, not
--     removals - that is the distinction this column exists to make.
SELECT COUNT(*)                                                AS total_accounts,
       SUM(CASE WHEN active = 0 AND deleted_at IS NULL  THEN 1 ELSE 0 END) AS suspended_accounts,
       SUM(CASE WHEN deleted_at IS NOT NULL             THEN 1 ELSE 0 END) AS removed_accounts
  FROM users;

-- 4d. The invariant the entity enforces: nothing may be removed and still
--     active. This must return 0 rows, now and after any future run.
SELECT id, email, active, deleted_at
  FROM users
 WHERE deleted_at IS NOT NULL
   AND active = 1;
