SELECT
  id,
  is_all_completed,
  user_id,
  training_date,
  part_code,
  menu,
  memo,
  duration,
  create_datetime,
  updated_datetime
FROM
  trainings
WHERE
  user_id = /* userId */1
AND
  training_date = /* date */'2026-03-07'
ORDER BY
  id ASC