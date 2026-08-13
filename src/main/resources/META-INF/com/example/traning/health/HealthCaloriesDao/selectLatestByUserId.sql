SELECT id, user_id, record_date, active_calories, total_calories, source, synced_at
FROM health_calories
WHERE user_id = /* userId */0
ORDER BY record_date DESC
LIMIT 1
