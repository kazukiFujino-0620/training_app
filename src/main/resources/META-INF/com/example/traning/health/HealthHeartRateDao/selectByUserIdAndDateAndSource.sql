SELECT id, user_id, record_date, avg_bpm, min_bpm, max_bpm, source, synced_at
FROM health_heart_rate
WHERE user_id = /* userId */0
  AND record_date = /* recordDate */'2020-01-01'
  AND source = /* source */'HEALTHKIT'
