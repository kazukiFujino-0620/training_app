SELECT x.item_name, x.weight
FROM (
  SELECT
    t.menu AS item_name,
    d.weight,
    ROW_NUMBER() OVER (PARTITION BY t.menu ORDER BY t.training_date DESC, d.weight DESC) AS rn
  FROM trainings t
  JOIN training_details d ON d.training_id = t.id
  WHERE t.user_id = /* userId */0
  AND t.training_date <= /* date */'2099-12-31'
  AND t.deleted_at IS NULL
  AND d.deleted_at IS NULL
  AND d.is_completed = 1
  AND d.set_type = 'MAIN'
  AND d.weight > 0
) x
WHERE x.rn = 1
