SELECT DISTINCT part_code
FROM trainings
WHERE user_id = /* userId */0
  AND training_date BETWEEN /* startDate */'2026-06-01' AND /* endDate */'2026-06-07'
  AND deleted_at IS NULL
  AND part_code IS NOT NULL
