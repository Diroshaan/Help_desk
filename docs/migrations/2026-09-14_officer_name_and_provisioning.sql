-- Migration: officers.full_name and the provisioned_by audit columns
-- Date: 2026-09-14   Author: Diroshaan S. (IT25101580)   Target: MySQL 8
--
-- Hibernate's 'update' can't add a NOT NULL column to a table that already has rows,
-- so this is done by hand. Run order: after 2026-09-13, before starting the new code.
-- Run it once - the ADD COLUMN steps fail if run again. Not needed on H2.
--
-- 1. Add full_name as nullable first, so it works on a table with rows.

ALTER TABLE officers
    ADD COLUMN full_name VARCHAR(120) NULL AFTER job_title;


-- 2. Backfill with the part of the email before '@'. This is only a placeholder;
-- real names are fixed from the admin screen. Email lives on users (JOINED).
UPDATE officers o
    JOIN users u ON u.id = o.id
SET o.full_name = SUBSTRING_INDEX(u.email, '@', 1)
WHERE o.full_name IS NULL OR o.full_name = '';


-- 3. Now make it NOT NULL, to match @Column(nullable = false) on Officer.
ALTER TABLE officers
    MODIFY COLUMN full_name VARCHAR(120) NOT NULL;


-- 4. Who provisioned each account. Nullable because older accounts and the first
-- (bootstrap) administrator have no provisioner. Administrators reference their own table.
ALTER TABLE officers
    ADD COLUMN provisioned_by BIGINT NULL;

ALTER TABLE administrators
    ADD COLUMN provisioned_by BIGINT NULL;


-- 5. Named foreign keys, so errors and the ER diagram are readable.
ALTER TABLE officers
    ADD CONSTRAINT fk_officer_provisioned_by
        FOREIGN KEY (provisioned_by) REFERENCES administrators (id);

ALTER TABLE administrators
    ADD CONSTRAINT fk_administrator_provisioned_by
        FOREIGN KEY (provisioned_by) REFERENCES administrators (id);


-- 6. Verify.
-- Expect: full_name VARCHAR(120) Null = NO, provisioned_by BIGINT Null = YES
DESCRIBE officers;


-- Expect: provisioned_by BIGINT Null = YES
DESCRIBE administrators;


-- Expect the two new constraints plus fk_category_department, fk_student_user,
-- fk_officer_user and fk_administrator_user.
SELECT constraint_name, table_name, column_name, referenced_table_name
FROM information_schema.key_column_usage
WHERE constraint_schema = DATABASE()
  AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name;


-- Expect 0.
SELECT COUNT(*) AS officers_still_unnamed
FROM officers
WHERE full_name IS NULL OR full_name = '';
