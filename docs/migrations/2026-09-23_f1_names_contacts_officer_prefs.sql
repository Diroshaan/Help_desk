-- ===========================================================================
--  F1 - given name / surname, multiple contact numbers, officer preferences
--  Branch: feat/f1-account-completion
-- ===========================================================================
--
--  !! ORDER MATTERS - READ BEFORE RUNNING !!
--
--  The hosted MySQL on Aiven is SHARED by the whole team. This script removes
--  two columns (students.full_name, students.contact_number) that the OLD code
--  on develop still reads and writes. So:
--
--    1. Test the branch on H2 first (no profile). H2 is rebuilt from the
--       entities on every start, so it needs none of this.
--    2. Merge the pull request into develop.
--    3. THEN run this script on Aiven, and only then start the new code with
--       the mysql profile. Tell the team to pull develop before they next run
--       against Aiven - old code on the new schema cannot insert students.
--
--  Run it BEFORE the new code first starts against Aiven. If the new code
--  starts first, Hibernate (ddl-auto=update) adds given_name as NOT NULL with
--  '' in every existing row and leaves full_name NOT NULL, so every student
--  shows a blank name and new registrations fail until this script runs. The
--  script is written to repair that state too, but it is better never entered.
--
--  Safe to run more than once: every step checks information_schema first
--  (MySQL has no ADD COLUMN IF NOT EXISTS), the same pattern as
--  2026-09-18_student_avatar.sql.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- 1. STUDENT NAME -> given_name + surname
--
--    Requirement 3.2: "store the student's name as separate given name and
--    surname components". The rule used to split existing names is the same
--    one the application uses (Student.setFullName): the LAST word is the
--    surname, everything before it is the given name, and a single-word name
--    has no surname. "L. S. N. Perera" -> "L. S. N." + "Perera".
-- ---------------------------------------------------------------------------
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

-- Backfill from full_name - only where the given name is missing or empty, so
-- a name a student has already edited under the new code is never overwritten.
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

-- Every student now has a given name, so it can become NOT NULL, as the
-- entity declares. (If this fails, a row has an empty full_name - find it with
-- SELECT id, student_id FROM students WHERE given_name IS NULL OR given_name = '';)
ALTER TABLE students MODIFY COLUMN given_name VARCHAR(120) NOT NULL;

-- The old column goes. Keeping it would store the same name twice, in two
-- places that could disagree after the next edit - and it is NOT NULL, so the
-- new code (which no longer writes it) could not insert a student at all.
SET @sql := IF(@has_full = 1,
    'ALTER TABLE students DROP COLUMN full_name',
    'SELECT ''full_name already removed'' AS step_1d');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- 2. CONTACT NUMBERS -> their own table
--
--    Requirement 3.2: "permit a student to record more than one contact
--    number". A multivalued attribute becomes a table keyed by its owner:
--    primary key (student_id, list_index), a foreign key to students, and the
--    existing single number copied in as each student's FIRST number.
--    Matches what Hibernate generates for Student.contactNumbers exactly.
-- ---------------------------------------------------------------------------
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


-- ---------------------------------------------------------------------------
-- 3. OFFICER PROFILE (US-04) - phone + two notification toggles
--
--    Defaults of 1 (on) for the toggles, so officers who existed before this
--    change keep receiving alerts. A NOT NULL BIT added without a default
--    would give them 0 - every existing officer silently switched off.
--    BIT(1) because that is what Hibernate uses for a Java boolean on MySQL,
--    so ddl-auto=validate will agree with this script later.
-- ---------------------------------------------------------------------------
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


-- ===========================================================================
--  4. VERIFICATION - run these and read the output.
-- ===========================================================================

-- Expect: given_name varchar(120) NO, surname varchar(120) YES,
--         and NO rows for full_name or contact_number.
SELECT column_name, column_type, is_nullable
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'students'
   AND column_name IN ('given_name', 'surname', 'full_name', 'contact_number')
 ORDER BY ordinal_position;

-- Expect 0. Any row here is a student with no usable name.
SELECT COUNT(*) AS students_without_given_name
  FROM students WHERE given_name IS NULL OR given_name = '';

-- Spot-check the split and the copied numbers.
SELECT s.id, s.student_id, s.given_name, s.surname, c.list_index, c.phone_number
  FROM students s
  LEFT JOIN student_contact_numbers c ON c.student_id = s.id
 ORDER BY s.id, c.list_index;

-- Expect three rows: contact_number, email_notifications_enabled,
-- portal_notifications_enabled; the two toggles NOT NULL with default b'1'.
SELECT column_name, column_type, is_nullable, column_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officers'
   AND column_name IN ('contact_number', 'email_notifications_enabled', 'portal_notifications_enabled');
