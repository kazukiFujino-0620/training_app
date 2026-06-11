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
AND
  training_date BETWEEN /* startDate */'2026-05-01' AND /* endDate */'2026-05-31'
AND
  deleted_at IS NULL
ORDER BY
  display_order ASC, id ASC