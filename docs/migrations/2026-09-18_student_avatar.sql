-- ===========================================================================
--  F1 - profile picture storage (Function 1 Update: "upload/update dynamic
--  profile avatars")
--  Branch: feat/f1-avatar-and-session-revocation
-- ===========================================================================
--
--  Run against the hosted MySQL. Safe to run on a database that has never seen
--  these columns AND on one where the application has already created them.
--
--  WHY THIS SCRIPT IS WRITTEN DEFENSIVELY RATHER THAN AS TWO PLAIN ADD COLUMNs
--  --------------------------------------------------------------------------
--  The first version of this file used ADD COLUMN, and it failed in practice
--  with:
--
--      SQL Error [1060] [42S21]: Duplicate column name 'profile_picture'
--
--  The reason is the order things happen in. spring.jpa.hibernate.ddl-auto is
--  'update', so the application creates any column the entity declares but the
--  database lacks - at startup, before anybody gets a chance to run a
--  migration. Both of these columns are nullable, so Hibernate was perfectly
--  willing to add them itself, and did.
--
--  What it added was not what was wanted. Hibernate 6 maps a plain byte[] to
--  VARBINARY, which MySQL resolves to TINYBLOB - 255 bytes. Every real
--  photograph overflowed it, and the failure surfaced as
--
--      SQL Error [1406] [22001]: Data truncation: Data too long for column
--      'profile_picture' at row 1
--
--  which GlobalExceptionHandler then reported to the student as "That value is
--  already in use by another account", because it labels every
--  DataIntegrityViolationException a duplicate. A 255-byte column, described as
--  a duplicate email address.
--
--  So the migration has to handle both states: add the column where it is
--  missing, and correct its TYPE where the application got there first. The
--  entity now carries columnDefinition = "MEDIUMBLOB" as well
--  (profile/entity/Student.java), so a genuinely fresh database gets the right
--  type without this script at all - but an existing one still needs the fix
--  below, because ddl-auto=update only ever ADDS. It never alters a column that
--  already exists, whatever the mapping now says.
--
--  On H2 (the no-profile default) none of this matters - that database is
--  in-memory and rebuilt from the entities on every start.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- 1. The image itself.
--
--    MEDIUMBLOB holds up to 16MB, comfortably above the 2MB the application
--    enforces in StudentService.MAX_AVATAR_BYTES. TINYBLOB (255 B) and BLOB
--    (64 KB) are both too small; LONGBLOB (4 GB) would be honest about nothing
--    and invites someone to raise the limit later without thinking about it.
--
--    The bytes live in the database rather than on disk because
--    src/main/resources is copied INTO the jar at build time - a file written
--    there at runtime lands in target/classes on a developer machine and
--    nowhere at all once packaged. This also matches what attachments and
--    resolutions already do, so one database backup covers every file in the
--    system.
--
--    MySQL has no ADD COLUMN IF NOT EXISTS, so the check is done against
--    information_schema and the statement built as text. That is also why the
--    verification queries at the bottom read information_schema: DBeaver's
--    column tab is a cached metadata panel and showed the type this script
--    INTENDED long after the server disagreed. information_schema asks the
--    server.
-- ---------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'students'
      AND column_name  = 'profile_picture'
);

SET @sql := IF(@col_exists = 0,
    'ALTER TABLE students ADD COLUMN profile_picture MEDIUMBLOB NULL AFTER profile_picture_url',
    'ALTER TABLE students MODIFY COLUMN profile_picture MEDIUMBLOB NULL');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- 2. The MIME type, so the download endpoint sets Content-Type correctly
--    instead of guessing from the bytes on every request.
--
--    VARCHAR(100) matches @Size(max = 100) on the entity field. The two are
--    kept deliberately in step: a @Column(length) without a matching @Size is
--    the bug pattern that produced the misleading "already in use" message
--    elsewhere in this schema - bean validation passes, the database rejects
--    the row, and the error arrives mislabelled.
-- ---------------------------------------------------------------------------
SET @type_col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'students'
      AND column_name  = 'profile_picture_type'
);

SET @sql := IF(@type_col_exists = 0,
    'ALTER TABLE students ADD COLUMN profile_picture_type VARCHAR(100) NULL AFTER profile_picture',
    'ALTER TABLE students MODIFY COLUMN profile_picture_type VARCHAR(100) NULL');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ===========================================================================
--  3. VERIFICATION - run these and read the output.
-- ===========================================================================

-- Expect exactly two rows:
--   profile_picture       mediumblob      YES
--   profile_picture_type  varchar(100)    YES
--
-- Read this rather than DESCRIBE or the DBeaver columns tab. If it says
-- tinyblob, section 1 did not take effect and any upload above 255 bytes will
-- still fail.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name   = 'students'
  AND column_name LIKE 'profile\_picture%'
ORDER BY ordinal_position;

-- Expect 0 until somebody uploads one. Useful afterwards as a quick check that
-- the bytes really landed rather than a zero-length row being stored.
SELECT COUNT(*) AS students_with_a_picture
FROM students
WHERE profile_picture IS NOT NULL
  AND LENGTH(profile_picture) > 0;
