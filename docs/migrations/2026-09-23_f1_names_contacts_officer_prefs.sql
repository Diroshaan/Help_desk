-- Migration: split student names into given_name + surname, move contact numbers
-- to their own table, and add officer phone and notification settings.
-- Date: 2026-09-23   Target: MySQL 8
--
-- Run order: after 2026-09-18, and before the new code first starts on MySQL. This
-- drops students.full_name and students.contact_number, which older code still uses,
-- so test on H2 first and tell the team to pull before running against the shared database.
-- Safe to re-run: each step checks information_schema first.
--
-- 1. Student name. Same rule as Student.setFullName: the last word is the surname,
-- the rest is the given name, and a one-word name has no surname.

SET @has_given := (SELECT COUNT(*) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'students' AND column_name = 'given_name');
SET @sql := IF(@has_given = 0,
    'ALTER TABLE students ADD COLUMN given_name VARCHAR(120) NULL AFTER student_id',
    'SELECT ''given_name already present'' AS step_1a');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_surname := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'students' AND column_name = 'surname');
SET @sql := IF(@has_surname = 0,
    'ALTER TABLE students ADD COLUMN surname VARCHAR(120) NULL AFTER given_name',
    'SELECT ''surname already present'' AS step_1b');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- Copy from full_name, but only where given_name is still empty, so names already
-- edited in the new code are not overwritten.
SET @has_full := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'students' AND column_name = 'full_name');
SET @sql := IF(@has_full = 1,
    'UPDATE students
        SET given_name = CASE
                WHEN LOCATE('' '', TRIM(full_name)) = 0 THEN TRIM(full_name)
                ELSE TRIM(LEFT(TRIM(full_name),
                               CHAR_LENGTH(TRIM(full_name)) - CHAR_LENGTH(SUBSTRING_INDEX(TRIM(full_name), '' '', -1))))
            END,
            surname = CASE
                WHEN LOCATE('' '', TRIM(full_name)) = 0 THEN NULL
                ELSE SUBSTRING_INDEX(TRIM(full_name), '' '', -1)
            END
      WHERE given_name IS NULL OR given_name = ''''',
    'SELECT ''full_name already removed - nothing to copy'' AS step_1c');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- Every student has a given name now, so make it NOT NULL. If this fails, find the row with:
-- SELECT id, student_id FROM students WHERE given_name IS NULL OR given_name = '';
ALTER TABLE students MODIFY COLUMN given_name VARCHAR(120) NOT NULL;


-- Drop the old column so the name isn't stored twice.
SET @sql := IF(@has_full = 1,
    'ALTER TABLE students DROP COLUMN full_name',
    'SELECT ''full_name already removed'' AS step_1d');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- 2. Contact numbers move to their own table (a student can have several).
-- The old single number becomes each student's first entry.
CREATE TABLE IF NOT EXISTS student_contact_numbers (
    student_id   BIGINT       NOT NULL,
    list_index   INT          NOT NULL,
    phone_number VARCHAR(30)  NOT NULL,
    PRIMARY KEY (student_id, list_index),
    CONSTRAINT fk_contact_number_student FOREIGN KEY (student_id) REFERENCES students (id)
);

SET @has_contact := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'students' AND column_name = 'contact_number');
SET @sql := IF(@has_contact = 1,
    'INSERT INTO student_contact_numbers (student_id, list_index, phone_number)
     SELECT s.id, 0, TRIM(s.contact_number)
       FROM students s
      WHERE s.contact_number IS NOT NULL AND TRIM(s.contact_number) <> ''''
        AND NOT EXISTS (SELECT 1 FROM student_contact_numbers c WHERE c.student_id = s.id)',
    'SELECT ''contact_number already removed - nothing to copy'' AS step_2a');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_contact = 1,
    'ALTER TABLE students DROP COLUMN contact_number',
    'SELECT ''contact_number already removed'' AS step_2b');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- 3. Officer phone and notification toggles. The toggles default to on, so existing
-- officers keep getting alerts.
SET @n := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'officers' AND column_name = 'contact_number');
SET @sql := IF(@n = 0,
    'ALTER TABLE officers ADD COLUMN contact_number VARCHAR(30) NULL',
    'SELECT ''officers.contact_number already present'' AS step_3a');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @n := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'officers' AND column_name = 'email_notifications_enabled');
SET @sql := IF(@n = 0,
    'ALTER TABLE officers ADD COLUMN email_notifications_enabled BIT(1) NOT NULL DEFAULT b''1''',
    'SELECT ''officers.email_notifications_enabled already present'' AS step_3b');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @n := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'officers' AND column_name = 'portal_notifications_enabled');
SET @sql := IF(@n = 0,
    'ALTER TABLE officers ADD COLUMN portal_notifications_enabled BIT(1) NOT NULL DEFAULT b''1''',
    'SELECT ''officers.portal_notifications_enabled already present'' AS step_3c');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- 4. Verify.
-- Expect given_name varchar(120) NO, surname varchar(120) YES, and no full_name or contact_number.
SELECT column_name, column_type, is_nullable
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'students'
   AND column_name IN ('given_name', 'surname', 'full_name', 'contact_number')
 ORDER BY ordinal_position;


-- Expect 0.
SELECT COUNT(*) AS students_without_given_name
  FROM students WHERE given_name IS NULL OR given_name = '';


-- Spot-check the split names and copied numbers.
SELECT s.id, s.student_id, s.given_name, s.surname, c.list_index, c.phone_number
  FROM students s
  LEFT JOIN student_contact_numbers c ON c.student_id = s.id
 ORDER BY s.id, c.list_index;


-- Expect three rows; the two toggles NOT NULL with default b'1'.
SELECT column_name, column_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officers'
   AND column_name IN ('contact_number', 'email_notifications_enabled', 'portal_notifications_enabled');
