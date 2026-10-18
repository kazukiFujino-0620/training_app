SELECT id, user_id, record_date, resting_bpm, source, created_at, updated_at
FROM health_resting_heart_rate
WHERE user_id = /* userId */0
ORDER BY record_date DESC
LIMIT 1
