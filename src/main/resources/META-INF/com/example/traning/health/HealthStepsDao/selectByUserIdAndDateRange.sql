SELECT id, user_id, record_date, step_count, source, synced_at
FROM health_steps
WHERE user_id = /* userId */0
  AND record_date BETWEEN /* from */'2020-01-01' AND /* to */'2020-01-31'
ORDER BY record_date
