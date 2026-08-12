SELECT id, user_id, sleep_date, start_time, end_time, duration_minutes, source, synced_at
FROM health_sleep
WHERE user_id = /* userId */0
  AND sleep_date BETWEEN /* from */'2020-01-01' AND /* to */'2020-01-31'
ORDER BY sleep_date
