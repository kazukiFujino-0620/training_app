SELECT id, user_id, record_date, active_calories, total_calories, source, synced_at
FROM health_calories
WHERE user_id = /* userId */0
  AND record_date = /* recordDate */'2020-01-01'
  AND source = /* source */'HEALTHKIT'
