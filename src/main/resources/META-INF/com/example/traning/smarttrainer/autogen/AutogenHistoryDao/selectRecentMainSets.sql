SELECT x.item_name, x.training_date, x.weight, x.reps
FROM (
  SELECT
    t.menu AS item_name,
    t.training_date,
    d.weight,
    d.reps,
    d.set_number,
    DENSE_RANK() OVER (PARTITION BY t.menu ORDER BY t.training_date DESC) AS rnk
  FROM trainings t
  JOIN training_details d ON d.training_id = t.id
  WHERE t.user_id = /* userId */0
  AND t.training_date <= /* date */'2099-12-31'
  AND t.deleted_at IS NULL
  AND d.deleted_at IS NULL
  AND d.is_completed = 1
  AND d.set_type = 'MAIN'
  AND d.weight > 0
  AND d.reps > 0
) x
WHERE x.rnk <= /* sessions */3
ORDER BY x.item_name, x.training_date DESC, x.set_number
