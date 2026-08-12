SELECT id, user_id, record_date, step_count, source, synced_at
FROM health_steps
WHERE user_id = /* userId */0
  AND record_date = /* recordDate */'2020-01-01'
  AND source = /* source */'HEALTHKIT'
