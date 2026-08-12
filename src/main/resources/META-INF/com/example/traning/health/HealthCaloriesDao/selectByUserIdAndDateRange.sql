SELECT id, user_id, record_date, active_calories, total_calories, source, synced_at
FROM health_calories
WHERE user_id = /* userId */0
  AND record_date BETWEEN /* from */'2020-01-01' AND /* to */'2020-01-31'
ORDER BY record_date
