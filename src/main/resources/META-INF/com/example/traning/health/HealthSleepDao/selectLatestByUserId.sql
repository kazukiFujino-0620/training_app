SELECT id, user_id, sleep_date, start_time, end_time, duration_minutes, source, synced_at
FROM health_sleep
WHERE user_id = /* userId */0
ORDER BY sleep_date DESC
LIMIT 1
