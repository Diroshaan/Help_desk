-- =====================================================================
-- Migration: introduce the user supertype (JOINED inheritance)
-- Date:      2026-09-13
-- Author:    Diroshaan S. (IT25101580)
-- Target:    MySQL 8 (the hosted Aiven instance)
-- =====================================================================
--
-- WHY THIS FILE EXISTS
-- --------------------
-- The application runs with spring.jpa.hibernate.ddl-auto=update, which is
-- convenient during development and cannot perform this change. 'update' only
-- ever ADDS - new tables, new columns, new indexes. It never drops a column,
-- never moves data between tables, and never converts a standalone table into
-- part of an inheritance hierarchy.
--
-- So on an EMPTY database, starting the application is enough: Hibernate
-- creates users, students, officers and administrators correctly from the
-- entities. On a database that already holds student rows, starting the
-- application produces a broken half-state:
--
--   * users, officers and administrators are created, all empty
--   * students keeps its old email / password / role / active / created_at
--     columns, now unmapped and ignored
--   * every existing student row has NO matching row in users
--
-- and because JOINED loads a student by joining students to users, every one of
-- those students becomes invisible. Nobody can log in. Nothing reports why.
--
-- This script performs the move properly. Run it ONCE, against a database that
-- still has the OLD schema, BEFORE starting the application with the new code.
--
--
-- WHICH PATH SHOULD YOU ACTUALLY TAKE?
-- ------------------------------------
-- For the development database, the honest answer is: don't run this. Drop the
-- affected tables and let Hibernate rebuild them, then re-register your test
-- account. It takes two minutes and carries no risk. The only cost is losing
-- test data that can be recreated in a minute.
--
--   DROP TABLE IF EXISTS activity_log;
--   DROP TABLE IF EXISTS students;
--   -- then start the application; Hibernate creates the new shape
--
-- This script is for the case where the data matters, and for the record: a
-- schema change on a live database is a migration, not a restart, and being
-- able to write the migration is the point of the exercise.
--
-- BACK UP FIRST either way. On Aiven: Service -> Backups.
--
-- =====================================================================


-- ---------------------------------------------------------------------
-- Step 0: safety. Run this and read the answer before going further.
-- ---------------------------------------------------------------------
-- Expect: a row count you recognise, and a 'users' table that does not exist.
-- If users already exists, the application has been started with the new code
-- against this database and the half-state described above is already present.
-- Stop, restore the backup, and start again.

SELECT COUNT(*) AS existing_students FROM students;
SHOW TABLES LIKE 'users';


-- ---------------------------------------------------------------------
-- Step 1: create the supertype table
-- ---------------------------------------------------------------------
-- Written by hand rather than letting Hibernate create it, because the order
-- matters: the data has to be copied in before the old columns are dropped, and
-- Hibernate would only create this table at application startup - by which time
-- it is already too late.

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(120) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    active      BIT(1)       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB;

-- Note BIT(1) for 'active' and DATETIME(6) for 'created_at': these are what
-- Hibernate generates for a Java boolean and LocalDateTime on MySQL. Using the
-- same types here means Hibernate's own schema validation agrees with this
-- table afterwards. Writing BOOLEAN or DATETIME instead would work, and then
-- ddl-auto=validate would reject the schema for a difference that does not
-- matter to anything except Hibernate's comparison.


-- ---------------------------------------------------------------------
-- Step 2: copy the shared columns out of students and into users
-- ---------------------------------------------------------------------
-- The id is carried over UNCHANGED. That is what keeps every existing foreign
-- key valid: activity_log.student_id, tickets.student_id, bookmarks.student_id
-- and feedback.student_id all hold student ids, and after this migration those
-- same numbers are still the ids of the same people. Generating fresh ids here
-- would orphan every one of those rows.

INSERT INTO users (id, email, password, active, created_at)
SELECT id, email, password, active, created_at
FROM   students;

-- Verify before continuing. These two numbers must match.
SELECT (SELECT COUNT(*) FROM students) AS students,
       (SELECT COUNT(*) FROM users)    AS users;


-- ---------------------------------------------------------------------
-- Step 3: hand the auto-increment over to users
-- ---------------------------------------------------------------------
-- Ids are now issued by users, so its counter must start above every id that
-- already exists. Skipping this step means the next registration tries to
-- insert id 1 into a table that already has one, and fails on the primary key
-- with an error that says nothing about the real cause.

SET @next_id = (SELECT IFNULL(MAX(id), 0) + 1 FROM users);
SET @sql = CONCAT('ALTER TABLE users AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- And students must STOP issuing its own ids. Under JOINED, a subtype row's id
-- is not generated - it is the id of its user row, copied down. Leaving
-- AUTO_INCREMENT on this column means MySQL would happily assign a different
-- one on an insert that omitted it.

ALTER TABLE students
    MODIFY COLUMN id BIGINT NOT NULL;


-- ---------------------------------------------------------------------
-- Step 4: remove the columns that have moved
-- ---------------------------------------------------------------------
-- Only safe because step 2 copied them and step 2's verification passed. Once
-- these are dropped the data exists in exactly one place.
--
-- 'role' is dropped without being copied anywhere, deliberately. Under the new
-- model the role is not stored at all - a row's presence in students IS the
-- fact that it is a student. See AppUser.getRole() for the reasoning. Every
-- row in this table is a student by definition, so no information is lost.

ALTER TABLE students
    DROP COLUMN email,
    DROP COLUMN password,
    DROP COLUMN role,
    DROP COLUMN active,
    DROP COLUMN created_at;


-- ---------------------------------------------------------------------
-- Step 5: tie the subtype to the supertype with a real foreign key
-- ---------------------------------------------------------------------
-- This is the constraint that makes the hierarchy a hierarchy rather than two
-- tables that happen to share numbers. After it, a student row cannot exist
-- without its user row, which is the database enforcing "every actor has
-- exactly one user record" rather than the application hoping so.
--
-- ON DELETE CASCADE is correct here and only here: deleting a user genuinely
-- should delete the student half of that same person, because they are one
-- record split across two tables, not two related records. Note the application
-- soft-deletes accounts (active = false) and never issues this DELETE - the
-- cascade is a correctness guarantee for manual intervention, not a routine
-- path.

ALTER TABLE students
    ADD CONSTRAINT fk_student_user
    FOREIGN KEY (id) REFERENCES users (id)
    ON DELETE CASCADE;


-- ---------------------------------------------------------------------
-- Step 6: the two new subtype tables
-- ---------------------------------------------------------------------
-- Empty to begin with. Hibernate would create these on startup, but creating
-- them here keeps the whole new shape in one reviewable file.

CREATE TABLE officers (
    id            BIGINT       NOT NULL,
    staff_number  VARCHAR(20)  NOT NULL,
    job_title     VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_officers_staff_number UNIQUE (staff_number),
    CONSTRAINT fk_officer_user FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE administrators (
    id            BIGINT       NOT NULL,
    staff_number  VARCHAR(20)  NULL,
    display_name  VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_administrators_staff_number UNIQUE (staff_number),
    CONSTRAINT fk_administrator_user FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- administrators.staff_number is NULL-able and UNIQUE at the same time, which
-- is not a contradiction: SQL permits any number of NULLs in a unique column,
-- because NULL is not equal to anything including another NULL. That is exactly
-- the "optional, but unique when present" rule wanted here, and it is why the
-- constraint does not have to be given up to make the column optional.


-- ---------------------------------------------------------------------
-- Step 7: verify
-- ---------------------------------------------------------------------

-- Every student has a user row. Must return 0.
SELECT COUNT(*) AS orphaned_students
FROM   students s
LEFT   JOIN users u ON u.id = s.id
WHERE  u.id IS NULL;

-- Every user row is claimed by exactly one subtype. Must return 0.
-- A user belonging to no subtype is unreachable - it can authenticate, and then
-- the application cannot tell what it is.
SELECT COUNT(*) AS unclassified_users
FROM   users u
LEFT   JOIN students s       ON s.id = u.id
LEFT   JOIN officers o       ON o.id = u.id
LEFT   JOIN administrators a ON a.id = u.id
WHERE  s.id IS NULL AND o.id IS NULL AND a.id IS NULL;

-- The foreign keys that now exist. Expect fk_student_user, fk_officer_user,
-- fk_administrator_user, and fk_category_department from the earlier
-- reference-data change.
SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
FROM   information_schema.KEY_COLUMN_USAGE
WHERE  TABLE_SCHEMA = DATABASE()
AND    REFERENCED_TABLE_NAME IS NOT NULL
ORDER  BY TABLE_NAME;


-- ---------------------------------------------------------------------
-- Step 8: after the application starts
-- ---------------------------------------------------------------------
-- Log in with an existing student account. If that works, the migration is
-- complete: it exercises users (the email and password lookup), students (the
-- join), and the inheritance mapping all at once.
--
-- If login fails with "No account found", the most likely cause is that step 2
-- was skipped or rolled back and users is empty. Check with:
--   SELECT COUNT(*) FROM users;
