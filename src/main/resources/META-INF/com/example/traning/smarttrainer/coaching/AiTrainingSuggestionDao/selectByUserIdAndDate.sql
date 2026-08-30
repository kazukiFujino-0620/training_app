SELECT id, user_id, target_date, part_code, comment, items_json, source, created_at
FROM ai_training_suggestions
WHERE user_id = /* userId */0
  AND target_date = /* targetDate */'2026-05-01'
