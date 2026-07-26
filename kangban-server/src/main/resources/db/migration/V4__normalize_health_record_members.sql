ALTER TABLE health_records
    ADD COLUMN member_id BIGINT NULL COMMENT 'FK to family_members.id; null means account owner' AFTER user_id,
    ADD KEY idx_hr_user_member_id (user_id, member_id);

UPDATE health_records
SET member_name = NULL
WHERE TRIM(member_name) IN ('自己', '本人');

UPDATE health_records hr
JOIN family_members fm
  ON fm.user_id = hr.user_id
 AND fm.name = hr.member_name
 AND fm.deleted_at IS NULL
SET hr.member_id = fm.id
WHERE hr.member_name IS NOT NULL;
