SELECT id, user_id, record_date, resting_bpm, source, created_at, updated_at
FROM health_resting_heart_rate
WHERE user_id = /* userId */0
  AND record_date = /* recordDate */'2020-01-01'
