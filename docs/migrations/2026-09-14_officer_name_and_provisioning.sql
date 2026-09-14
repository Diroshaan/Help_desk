-- ===========================================================================
--  Shared user hierarchy: officer display name + provisioning audit trail
--  Branch: feat/officer-name-and-auth-me
--  Author: Diroshaan S (IT25101580) - shared common/user is maintainer-owned
-- ===========================================================================
--
--  WHY A HAND-WRITTEN MIGRATION WHEN ddl-auto IS 'update'
--  -----------------------------------------------------
--  Hibernate's 'update' only ever ADDS. It will not add a NOT NULL column to a
--  table that already has rows (there is no value to put in them), and it never
--  tightens an existing column from NULL to NOT NULL. What it does instead is
--  log a warning and carry on starting - so the application looks healthy while
--  the constraint the entity declares is simply not there in the database.
--
--  That gap is the whole reason this file exists. Run it against the hosted
--  MySQL BEFORE starting the app with the new entities.
--
--  On H2 (the no-profile default) none of this is needed: that database is
--  recreated from the entities on every start, so it already matches.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- 1. officers.full_name - added NULLABLE first, deliberately.
--
--    Adding it as NOT NULL in one statement fails on a populated table. Three
--    steps (add nullable -> backfill -> tighten) is the standard way to add a
--    required column to a table that already has data, and each step is
--    individually safe to re-run or abandon.
-- ---------------------------------------------------------------------------
ALTER TABLE officers
    ADD COLUMN full_name VARCHAR(120) NULL AFTER job_title;


-- ---------------------------------------------------------------------------
-- 2. Backfill.
--
--    The local part of the login email ("j.silva@uni.lk" -> "j.silva") is a
--    PLACEHOLDER, not a real name. The only job of this step is to get every
--    existing row to a non-null value so step 3 can succeed; real names are
--    corrected through the admin screen afterwards.
--
--    Stated plainly rather than dressed up, because inventing data and not
--    saying so is how a database ends up with values nobody can account for.
--    The join is to users because email lives on the supertype table, not on
--    officers - that is the JOINED inheritance strategy showing through.
-- ---------------------------------------------------------------------------
UPDATE officers o
    JOIN users u ON u.id = o.id
SET o.full_name = SUBSTRING_INDEX(u.email, '@', 1)
WHERE o.full_name IS NULL OR o.full_name = '';


-- ---------------------------------------------------------------------------
-- 3. Tighten to NOT NULL, matching @Column(nullable = false) on Officer.
--
--    The entity and the schema must agree. If they disagree, the application
--    enforces a rule the database does not - which holds only for writes that
--    go through the application, and this database is also written to by hand
--    from the Aiven console.
-- ---------------------------------------------------------------------------
ALTER TABLE officers
    MODIFY COLUMN full_name VARCHAR(120) NOT NULL;


-- ---------------------------------------------------------------------------
-- 4. provisioned_by - the audit trail for F6's account provisioning (WBHD-35).
--
--    NULLABLE, and that is a modelling decision rather than a convenience:
--    accounts created before this column existed have no provisioner, and the
--    bootstrap administrator has none by definition - AdminBootstrapSeeder
--    creates it precisely because no administrator exists yet. NOT NULL would
--    force a fabricated value into an audit column, which defeats the point of
--    having one.
--
--    administrators.provisioned_by is SELF-REFERENCING: an administrator is
--    provisioned by another administrator, both rows in the same table.
-- ---------------------------------------------------------------------------
ALTER TABLE officers
    ADD COLUMN provisioned_by BIGINT NULL;

ALTER TABLE administrators
    ADD COLUMN provisioned_by BIGINT NULL;


-- ---------------------------------------------------------------------------
-- 5. The foreign keys, named.
--
--    Hibernate would otherwise generate names like FKq7x2m1k4d8s. A named
--    constraint is readable in the ER diagram the module asks for, and it is
--    what an error message quotes when a write is rejected - "fk_officer_
--    provisioned_by" says what went wrong; "FKq7x2m1k4d8s" says nothing.
-- ---------------------------------------------------------------------------
ALTER TABLE officers
    ADD CONSTRAINT fk_officer_provisioned_by
        FOREIGN KEY (provisioned_by) REFERENCES administrators (id);

ALTER TABLE administrators
    ADD CONSTRAINT fk_administrator_provisioned_by
        FOREIGN KEY (provisioned_by) REFERENCES administrators (id);


-- ===========================================================================
--  6. VERIFICATION - run these and read the output. Do not skip.
-- ===========================================================================

-- Expect: full_name      VARCHAR(120)  Null = NO
--         provisioned_by BIGINT        Null = YES
DESCRIBE officers;

-- Expect: provisioned_by BIGINT        Null = YES
DESCRIBE administrators;

-- Expect both new constraints listed, alongside the four that already exist
-- (fk_category_department, fk_student_user, fk_officer_user,
--  fk_administrator_user).
SELECT constraint_name, table_name, column_name, referenced_table_name
FROM information_schema.key_column_usage
WHERE constraint_schema = DATABASE()
  AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name;

-- Expect 0. Any row here would fail the NOT NULL constraint on the next write
-- and means step 2 did not do its job.
SELECT COUNT(*) AS officers_still_unnamed
FROM officers
WHERE full_name IS NULL OR full_name = '';
