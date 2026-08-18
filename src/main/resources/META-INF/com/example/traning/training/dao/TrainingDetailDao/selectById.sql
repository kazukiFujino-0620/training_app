SELECT
  id,
  training_id,
  set_number,
  weight,
  reps,
  count,
  set_type,
  is_completed,
  create_datetime,
  updated_datetime,
  deleted_at,
  duration_min,
  distance_km,
  avg_heart_rate_bpm,
  calories_kcal
FROM
  training_details
WHERE
  id = /* id */0
AND
  deleted_at IS NULL
