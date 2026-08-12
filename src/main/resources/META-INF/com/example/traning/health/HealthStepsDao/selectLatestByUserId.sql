SELECT id, user_id, record_date, step_count, source, synced_at
FROM health_steps
WHERE user_id = /* userId */0
ORDER BY record_date DESC
LIMIT 1
