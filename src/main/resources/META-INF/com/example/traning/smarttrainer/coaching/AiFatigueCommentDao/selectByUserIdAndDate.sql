SELECT id, user_id, target_date, comment, source, created_at
FROM ai_fatigue_comments
WHERE user_id = /* userId */0
  AND target_date = /* targetDate */'2026-05-01'
