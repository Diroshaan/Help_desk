-- ===========================================================================
--  F3 - Ticket Lifecycle & Feedback: unique bookmarks and unique feedback
--  (Amarasinghe S. D, IT25103424)
--  Branch: feat/f3-final-backend
--  Supports: #42 (WBHD-52), DB requirement "at most one feedback per
--            ticket", and one bookmark per student per ticket.
-- ===========================================================================
--
--  WHY THIS IS NEEDED
--
--  BookmarkService.createBookmark and FeedbackService.submitFeedback check
--  existsBy... and then save(). That is check-then-act: two clicks at the
--  same moment both pass the check and both insert. Only a UNIQUE constraint
--  in the database stops the second one. Bookmark and Feedback now declare
--  them (uq_bookmark_student_ticket, uq_feedback_ticket); this script adds
--  them to the tables that already exist on Aiven.
--
--  WHAT IT DOES
--
--    1. Reports how many duplicate rows exist (section 1).
--    2. Deletes the duplicates, KEEPING THE LOWEST id - the first bookmark /
--       first feedback the student made (section 2). Unlike the foreign-key
--       script (2026-10-01), deleting is right here: a duplicate is not
--       evidence of missing data, it is the same row twice, and the
--       constraint cannot be added while it is there. Nothing references
--       bookmarks or feedback by foreign key, so no other row is affected.
--    3. Adds each constraint only if one with that name isn't already there
--       (re-runnable) (section 3).
--    4. Verification SELECTs (section 4).
--
--  bookmark_folders.colour needs nothing: the column is already
--  VARCHAR(7), and the #RRGGBB check is in BookmarkFolderRequest.
--
--  ORDER (Team guide 5.3)
--
--    1. Merge the pull request.
--    2. Tell the group to stop running against Aiven.
--    3. Run this whole script in DBeaver (Alt+X) on defaultdb - BEFORE
--       starting the app on the mysql profile. (ddl-auto=update would try to
--       add the same constraints on startup and fail, with only a warning,
--       if duplicates are still there.)
--    4. Read the result rows: each constraint line should say "added" or
--       "already present", and section 4 should show 0 duplicates.
--    5. Start the app once on the mysql profile and click through; tell the
--       group to pull develop.
--
--  H2 (local runs and the tests) is not affected: H2 is rebuilt from the
--  entities on every start and never runs this script.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- 1. REPORT DUPLICATES (before anything is changed)
-- ---------------------------------------------------------------------------

SELECT 'bookmarks: duplicate rows to delete' AS check_name, COUNT(*) AS duplicate_rows
FROM `bookmarks` b1
WHERE EXISTS (SELECT 1 FROM `bookmarks` b2
              WHERE b2.`student_id` = b1.`student_id`
                AND b2.`ticket_id` = b1.`ticket_id`
                AND b2.`id` < b1.`id`)
UNION ALL
SELECT 'feedback: duplicate rows to delete' AS check_name, COUNT(*) AS duplicate_rows
FROM `feedback` f1
WHERE EXISTS (SELECT 1 FROM `feedback` f2
              WHERE f2.`ticket_id` = f1.`ticket_id`
                AND f2.`id` < f1.`id`);


-- ---------------------------------------------------------------------------
-- 2. DELETE DUPLICATES, KEEPING THE LOWEST id
--    For each (student, ticket) bookmark pair and each ticket's feedback,
--    every row except the earliest is removed.
-- ---------------------------------------------------------------------------

DELETE b1 FROM `bookmarks` b1
JOIN `bookmarks` b2
  ON b1.`student_id` = b2.`student_id`
 AND b1.`ticket_id` = b2.`ticket_id`
 AND b1.`id` > b2.`id`;

DELETE f1 FROM `feedback` f1
JOIN `feedback` f2
  ON f1.`ticket_id` = f2.`ticket_id`
 AND f1.`id` > f2.`id`;


-- ---------------------------------------------------------------------------
-- 3. ADD THE UNIQUE CONSTRAINTS
--    Each block: skip if a constraint with that name is present, otherwise
--    ALTER TABLE ... ADD CONSTRAINT.
-- ---------------------------------------------------------------------------

-- 3.1 uq_bookmark_student_ticket: one bookmark per student per ticket
SET @has_uq := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'bookmarks'
                  AND constraint_name = 'uq_bookmark_student_ticket' AND constraint_type = 'UNIQUE');
SET @sql := IF(@has_uq > 0,
    'SELECT ''uq_bookmark_student_ticket: already present'' AS result',
    'ALTER TABLE `bookmarks` ADD CONSTRAINT `uq_bookmark_student_ticket` UNIQUE (`student_id`, `ticket_id`)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_uq = 0, 'SELECT ''uq_bookmark_student_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3.2 uq_feedback_ticket: at most one feedback per ticket
SET @has_uq := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND table_name = 'feedback'
                  AND constraint_name = 'uq_feedback_ticket' AND constraint_type = 'UNIQUE');
SET @sql := IF(@has_uq > 0,
    'SELECT ''uq_feedback_ticket: already present'' AS result',
    'ALTER TABLE `feedback` ADD CONSTRAINT `uq_feedback_ticket` UNIQUE (`ticket_id`)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@has_uq = 0, 'SELECT ''uq_feedback_ticket: added'' AS result', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;


-- ---------------------------------------------------------------------------
-- 4. VERIFY
--    Expect: both constraints listed, and 0 duplicate rows in both tables.
-- ---------------------------------------------------------------------------

SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN ('uq_bookmark_student_ticket', 'uq_feedback_ticket')
ORDER BY table_name;

SELECT 'bookmarks: duplicates remaining' AS check_name, COUNT(*) AS duplicate_rows
FROM (SELECT `student_id`, `ticket_id` FROM `bookmarks`
      GROUP BY `student_id`, `ticket_id` HAVING COUNT(*) > 1) d
UNION ALL
SELECT 'feedback: duplicates remaining' AS check_name, COUNT(*) AS duplicate_rows
FROM (SELECT `ticket_id` FROM `feedback`
      GROUP BY `ticket_id` HAVING COUNT(*) > 1) d;
