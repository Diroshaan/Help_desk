-- ===========================================================================
--  F2 - Advanced Ticket Request Engine: ticket columns and attachment
--  provenance (Chamikara A. K, IT25102416)
--  Branch: feat/f2-final-backend
--  Supports: F2-N6/N1 (file storage and type), #43 (ENUM trap and column
--            bounds), F2-N2 (optimistic locking column), #44 (attachment
--            uploader and kind), #45 (status-change history table)
-- ===========================================================================
--
--  WHAT THIS FIXES
--
--  1. attachments.data was mapped bare @Lob, which Hibernate/MySQL resolves
--     to TINYBLOB (255 bytes) - every real file failed. The entity now says
--     MEDIUMBLOB (16 MB); this script corrects the column that already
--     exists on Aiven, the same fix 2026-09-18_student_avatar.sql made for
--     Student.profilePicture, and for the same reason.
--  2. tickets.status and tickets.priority were @Enumerated(STRING) with no
--     @JdbcTypeCode, which MySQL resolves to a native ENUM column.
--     ddl-auto=update can ADD a column but never ALTERs one that exists, so
--     a native ENUM can never gain a new value without a migration like
--     this one - and by then every row needs converting anyway. VARCHAR(20)
--     never has this problem.
--  3. tickets.subject/category had no explicit length (Hibernate's default
--     is VARCHAR(255)); the entity now bounds them to match their matching
--     request-DTO @Size limits (150, 120). NOTE: the F2 build notes for
--     this feature said to leave tickets.category untouched, reasoning that
--     its foreign key to categories.name (VARCHAR(120)) already bounds its
--     values. Diroshaan asked for category to be brought to VARCHAR(120)
--     here too, so the column's declared width matches the entity exactly,
--     not only its values incidentally - shrinking it is safe for exactly
--     the reason the original note gave (every value already fits).
--  4. tickets.version (F2-N2, optimistic locking) does not exist on Aiven
--     yet.
--  5. attachments.uploaded_by_user_id and attachments.kind (#44) do not
--     exist on Aiven yet.
--  6. ticket_status_changes (#45) is a new table; Hibernate creates it, but
--     changed_by_user_id -> users(id) is NOT declared on the entity (by
--     design - see TicketStatusChange.java's comment), so nothing but this
--     script will ever add that key.
--
--  ORDER (Team guide 5.3, adapted for this script)
--
--    1. Merge this pull request.
--    2. Tell the group to stop running against Aiven.
--    3. Start the application ONCE on the mysql profile. ddl-auto=update
--       then does what it always does: it creates ticket_status_changes
--       (a table that did not exist) and adds any new NULLABLE/DEFAULTed
--       column it can (version, uploaded_by_user_id, kind) - but it will
--       NOT fix the ENUM columns, the TINYBLOB, the unbounded VARCHARs, or
--       any foreign key, because none of those are "a column that is
--       missing" from its point of view.
--    4. Stop the application again and run this whole script in DBeaver
--       (Alt+X) on defaultdb.
--    5. Read the result rows: every line should say "added"/"already
--       present", or name an orphan/length count to look at.
--    6. Start the application again and tell the group to pull develop.
--
--  Every step below is state-aware (information_schema first, then
--  PREPARE/EXECUTE) and safe to run more than once, the same pattern as
--  2026-10-01_referential_integrity.sql. The table-existence guards on
--  section 6 also make it safe even if run BEFORE step 3 above, though that
--  is not the order above actually asks for.
--
--  H2 (local runs and the tests) is not affected: that database is rebuilt
--  from the entities, with their correct types, on every start.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- 1. attachments.data: TINYBLOB (or whatever Hibernate first created) -> MEDIUMBLOB.
--    MODIFY COLUMN to the same type is harmless, so this is run unconditionally
--    rather than guarded - there is no state where running it is wrong.
-- ---------------------------------------------------------------------------
ALTER TABLE `attachments` MODIFY COLUMN `data` MEDIUMBLOB NOT NULL;
SELECT 'attachments.data: set to MEDIUMBLOB' AS result;


-- ---------------------------------------------------------------------------
-- 2. tickets.status / tickets.priority: native ENUM -> VARCHAR(20).
--    Guarded on data_type = 'enum' so a second run (already VARCHAR) is a
--    no-op rather than a pointless repeat MODIFY.
-- ---------------------------------------------------------------------------
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


-- ---------------------------------------------------------------------------
-- 3. tickets.subject -> VARCHAR(150) NOT NULL, only if every existing value
--    already fits. Nothing is ever truncated by this script: a value that
--    does not fit is reported and the column is left as it was.
-- ---------------------------------------------------------------------------
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


-- ---------------------------------------------------------------------------
-- 4. tickets.category -> VARCHAR(120) NOT NULL, same safety as subject above.
--    See the header note on why this column is touched even though the F2
--    build notes originally said to leave it: every value already fits the
--    categories.name foreign key's own VARCHAR(120), so shrinking the
--    declared width to match the entity is safe.
-- ---------------------------------------------------------------------------
SET @current_type := (SELECT column_type FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'category');
SET @max_len := (SELECT COALESCE(MAX(CHAR_LENGTH(category)), 0) FROM `tickets`);
SET @sql := IF(@current_type = 'varchar(120)',
    'SELECT ''tickets.category: already varchar(120)'' AS result',
    IF(@max_len > 120,
       CONCAT('SELECT ''tickets.category: NOT modified - longest existing value is ', @max_len,
              ' characters, see section 7'' AS result'),
       'ALTER TABLE `tickets` MODIFY COLUMN `category` VARCHAR(120) NOT NULL'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@current_type <> 'varchar(120)' AND @max_len <= 120,
    'SELECT ''tickets.category: set to VARCHAR(120)'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- ---------------------------------------------------------------------------
-- 5. tickets.version (F2-N2, optimistic locking).
--    BIGINT NOT NULL DEFAULT 0: MySQL backfills every existing row with 0
--    the moment the column is added, because a DEFAULT on ADD COLUMN NOT
--    NULL applies retroactively. The UPDATE below is a defensive backstop
--    only - it matters if ddl-auto=update got here first and, for some
--    MySQL configuration, left the column nullable.
-- ---------------------------------------------------------------------------
SET @has_version := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'version');
SET @sql := IF(@has_version = 0,
    'ALTER TABLE `tickets` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0',
    'SELECT ''tickets.version: already present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_version = 0, 'SELECT ''tickets.version: added, existing rows set to 0'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE `tickets` SET `version` = 0 WHERE `version` IS NULL;


-- ---------------------------------------------------------------------------
-- 6. attachments.uploaded_by_user_id (#44, contract C8).
--    Added nullable first, backfilled, then tightened to NOT NULL - the
--    same three-step shape as 2026-09-26_user_soft_removal.sql's deleted_at,
--    except this column ends up NOT NULL once every row has a value.
-- ---------------------------------------------------------------------------
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'attachments'
                   AND column_name = 'uploaded_by_user_id');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE `attachments` ADD COLUMN `uploaded_by_user_id` BIGINT NULL',
    'SELECT ''attachments.uploaded_by_user_id: column already present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- Backfilled <2026-10-02>: every existing attachment was uploaded by the
-- ticket's own student, because only students could upload at the time
-- these rows were written. "OR = 0" alongside "IS NULL": if the application
-- started on this profile before this script ran, ddl-auto=update would
-- have added this column mapped NOT NULL straight away (the entity declares
-- it so), and a primitive-shaped Long with no value supplied lands as 0 -
-- which is never a real user id - rather than NULL.
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

-- The foreign key, same state-aware pattern as every key in 2026-10-01.
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


-- ---------------------------------------------------------------------------
-- 7. attachments.kind (#44, contract C8).
--    ADD COLUMN ... NOT NULL DEFAULT 'SUBMISSION' backfills every existing
--    row the moment the column is added - no separate UPDATE needed, and
--    correctly so: every attachment ever created through this feature was
--    a submission file, since RESOLUTION has never been written by any code.
-- ---------------------------------------------------------------------------
SET @has_kind := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'attachments' AND column_name = 'kind');
SET @sql := IF(@has_kind = 0,
    'ALTER TABLE `attachments` ADD COLUMN `kind` VARCHAR(20) NOT NULL DEFAULT ''SUBMISSION''',
    'SELECT ''attachments.kind: already present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_kind = 0,
    'SELECT ''attachments.kind: added, existing rows set to SUBMISSION'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- ---------------------------------------------------------------------------
-- 8. ticket_status_changes (#45): the table itself is created by Hibernate
--    (ddl-auto=update, step 3 of the order above) - including
--    fk_status_changes_ticket, because that key IS declared on the entity
--    (a read-only @ManyToOne) and a brand-new table's CREATE TABLE carries
--    every constraint the entity declares.
--
--    changed_by_user_id -> users(id) is different: TicketStatusChange keeps
--    changed_by_user_id a plain Long (matching every other ticket-side
--    reference, e.g. Ticket.studentId), so nothing in the entity will ever
--    create this key. It exists only here - the same split used for the 21
--    keys in 2026-10-01_referential_integrity.sql, all of which back plain
--    Long/String references rather than @ManyToOne associations.
--
--    Guarded on the table existing, so this is harmless even if run before
--    step 3 of the order above ever creates the table.
-- ---------------------------------------------------------------------------
SET @has_table := (SELECT COUNT(*) FROM information_schema.tables
                   WHERE table_schema = DATABASE() AND table_name = 'ticket_status_changes');
SET @sql := IF(@has_table = 0,
    'SELECT ''ticket_status_changes: table not found yet - start the application once on this profile, then re-run this script'' AS result',
    'SELECT ''ticket_status_changes: table present'' AS result');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_fk := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'ticket_status_changes'
                  AND constraint_name = 'fk_status_changes_changed_by' AND constraint_type = 'FOREIGN KEY');

-- changed_by_user_id is nullable by design (null = unknown officer, see
-- TicketStatusChange.java), so only non-null values count as orphans here.
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


-- ===========================================================================
--  9. VERIFICATION - run these and read the output.
-- ===========================================================================

-- 9a. tickets: expect MEDIUMBLOB is irrelevant here, but status/priority
--     should both say varchar(20), subject varchar(150), category
--     varchar(120), version bigint NOT NULL with a default of 0.
SELECT column_name, column_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'tickets'
   AND column_name IN ('status', 'priority', 'subject', 'category', 'version')
 ORDER BY ordinal_position;

-- 9b. attachments: data should say mediumblob; uploaded_by_user_id bigint
--     NOT NULL; kind varchar(20) NOT NULL default 'SUBMISSION'.
SELECT column_name, column_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'attachments'
   AND column_name IN ('data', 'uploaded_by_user_id', 'kind')
 ORDER BY ordinal_position;

-- 9c. The two foreign keys this script owns, plus the one Hibernate should
--     already have created on ticket_status_changes. Expect 3 rows.
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

-- 9d. Counts that must all be 0. A non-zero count here means a step above
--     was skipped - re-read its result row to see why.
SELECT
    (SELECT COUNT(*) FROM `tickets` WHERE `version` IS NULL)               AS tickets_null_version,
    (SELECT COUNT(*) FROM `attachments` WHERE `uploaded_by_user_id` IS NULL) AS attachments_null_uploader,
    (SELECT COUNT(*) FROM `attachments` WHERE `kind` IS NULL)               AS attachments_null_kind,
    (SELECT COUNT(*) FROM `tickets` WHERE CHAR_LENGTH(`subject`) > 150)     AS tickets_subject_too_long,
    (SELECT COUNT(*) FROM `tickets` WHERE CHAR_LENGTH(`category`) > 120)    AS tickets_category_too_long;
