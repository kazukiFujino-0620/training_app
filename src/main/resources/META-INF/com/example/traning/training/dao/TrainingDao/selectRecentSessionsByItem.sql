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
  user_id = /* userId */0
AND menu = /* itemName */''
AND training_date < /* before */'2099-12-31'
AND deleted_at IS NULL
ORDER BY
  training_date DESC
LIMIT /* limit */7
