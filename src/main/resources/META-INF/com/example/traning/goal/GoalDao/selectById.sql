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
WHERE id = /* id */0
