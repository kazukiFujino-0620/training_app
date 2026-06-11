SELECT COALESCE(MAX(display_order), -1)
FROM trainings
WHERE user_id      = /* userId */0
  AND training_date = /* date */'2026-06-07'
  AND deleted_at IS NULL
