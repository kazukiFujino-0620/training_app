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
  user_id = /* userId */0
AND training_date = /* date */'2026-01-01'
AND superset_group_id IS NULL
AND deleted_at IS NULL
ORDER BY id ASC
