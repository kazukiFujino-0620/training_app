SELECT
    part_code,
    COUNT(id) AS session_count
FROM trainings
WHERE user_id      = /* userId */0
  AND training_date BETWEEN /* startDate */'2026-05-01' AND /* endDate */'2026-05-31'
  AND deleted_at IS NULL
  AND part_code IS NOT NULL
GROUP BY part_code
ORDER BY part_code
