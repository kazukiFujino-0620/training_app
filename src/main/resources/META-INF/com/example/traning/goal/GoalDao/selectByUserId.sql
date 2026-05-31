SELECT
  id,
  user_id,
  item_name,
  target_weight,
  target_reps,
  target_date,
  status,
  memo,
  created_at,
  updated_at
FROM training_goals
WHERE user_id = /* userId */0
ORDER BY
  CASE status WHEN 'ACTIVE' THEN 0 WHEN 'ACHIEVED' THEN 1 ELSE 2 END,
  target_date ASC
