SELECT COUNT(id)
FROM trainings
WHERE user_id      = /* userId */0
  AND training_date BETWEEN /* startDate */'2026-05-26' AND /* endDate */'2026-06-01'
  AND deleted_at IS NULL
