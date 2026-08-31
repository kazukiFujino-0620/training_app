SELECT id, user_id, week_start_date, items_json, source, created_at
FROM ai_training_suggestions
WHERE user_id = /* userId */0
  AND week_start_date = /* weekStartDate */'2026-05-01'
