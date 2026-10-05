-- Migration: move the shared login columns out of students into a new users table
-- (JOINED inheritance), and add the officers and administrators tables.
-- Date: 2026-09-13   Author: Diroshaan S. (IT25101580)   Target: MySQL 8
--
-- Only needed on a database that still has the old students-only schema; on an
-- empty database Hibernate creates everything. Run order: first of the files in
-- docs/migrations (run them in date order), before starting the new code.
-- Run it ONCE - it is not safe to re-run (step 1 fails if users exists). Back up first.
--
-- Step 0: check the starting point. Expect your student count and no users table.
-- If users already exists, restore the backup before going on.

SELECT COUNT(*) AS existing_students FROM students;
SHOW TABLES LIKE 'users';


-- Step 1: create the users table. Done by hand because the data must be copied in
-- before the old columns are dropped. Types match what Hibernate generates.
CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(120) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    active      BIT(1)       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB;


-- Step 2: copy the shared columns across. Ids are kept the same so every existing
-- foreign key to a student still points at the right person.
INSERT INTO users (id, email, password, active, created_at)
SELECT id, email, password, active, created_at
FROM   students;


-- These two counts must match before you continue.
SELECT (SELECT COUNT(*) FROM students) AS students,
       (SELECT COUNT(*) FROM users)    AS users;


-- Step 3: users now issues ids, so start its counter above the highest existing id.
SET @next_id = (SELECT IFNULL(MAX(id), 0) + 1 FROM users);
SET @sql = CONCAT('ALTER TABLE users AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- Under JOINED a student's id comes from its users row, so drop AUTO_INCREMENT here.
ALTER TABLE students
    MODIFY COLUMN id BIGINT NOT NULL;


-- Step 4: drop the columns that moved. role is not copied: being in the students
-- table is what makes a user a student.
ALTER TABLE students
    DROP COLUMN email,
    DROP COLUMN password,
    DROP COLUMN role,
    DROP COLUMN active,
    DROP COLUMN created_at;


-- Step 5: link students to users. CASCADE only matters for manual deletes - the app
-- itself never hard-deletes accounts.
ALTER TABLE students
    ADD CONSTRAINT fk_student_user
    FOREIGN KEY (id) REFERENCES users (id)
    ON DELETE CASCADE;


-- Step 6: the two new subtype tables (empty for now). A UNIQUE column can hold
-- many NULLs, so administrators.staff_number is optional but unique when set.
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


-- Step 7: verify.
-- Every student has a users row. Must return 0.
SELECT COUNT(*) AS orphaned_students
FROM   students s
LEFT   JOIN users u ON u.id = s.id
WHERE  u.id IS NULL;


-- Every user belongs to exactly one subtype. Must return 0.
SELECT COUNT(*) AS unclassified_users
FROM   users u
LEFT   JOIN students s       ON s.id = u.id
LEFT   JOIN officers o       ON o.id = u.id
LEFT   JOIN administrators a ON a.id = u.id
WHERE  s.id IS NULL AND o.id IS NULL AND a.id IS NULL;


-- Foreign keys that now exist: expect fk_student_user, fk_officer_user,
-- fk_administrator_user and fk_category_department.
SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
FROM   information_schema.KEY_COLUMN_USAGE
WHERE  TABLE_SCHEMA = DATABASE()
AND    REFERENCED_TABLE_NAME IS NOT NULL
ORDER  BY TABLE_NAME;


-- Step 8: start the app and log in as an existing student. If login fails with
-- "No account found", check that users is not empty (step 2).
