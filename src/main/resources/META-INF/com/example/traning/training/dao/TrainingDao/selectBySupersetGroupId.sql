SELECT
  id,
  is_all_completed,
  user_id,
  training_date,
  part_code,
  superset_group_id,
  menu,
  memo,
  duration,
  create_datetime,
  updated_datetime
FROM
  trainings
WHERE
  superset_group_id = /* supersetGroupId */0
AND deleted_at IS NULL
ORDER BY id ASC
