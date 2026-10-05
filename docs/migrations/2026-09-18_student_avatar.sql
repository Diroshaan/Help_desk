-- Migration: students.profile_picture and profile_picture_type (profile avatars)
-- Date: 2026-09-18   Target: MySQL 8
--
-- Hibernate may already have added profile_picture as TINYBLOB (255 bytes), which
-- is too small for a photo. This adds each column if missing, or fixes its type if not.
-- Safe to re-run. Run order: after 2026-09-14. Not needed on H2.
--
-- 1. The image. MEDIUMBLOB (16MB) is well above the app's 2MB limit. MySQL has no
-- ADD COLUMN IF NOT EXISTS, so we check information_schema and build the statement.

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


-- 2. The MIME type, used as Content-Type when the picture is downloaded.
-- VARCHAR(100) matches @Size(max = 100) on the entity.
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


-- 3. Verify. Expect profile_picture mediumblob and profile_picture_type varchar(100).
-- Use this query, not DESCRIBE or DBeaver's cached columns tab.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name   = 'students'
  AND column_name LIKE 'profile\_picture%'
ORDER BY ordinal_position;


-- Pictures stored so far (0 until someone uploads one).
SELECT COUNT(*) AS students_with_a_picture
FROM students
WHERE profile_picture IS NOT NULL
  AND LENGTH(profile_picture) > 0;
