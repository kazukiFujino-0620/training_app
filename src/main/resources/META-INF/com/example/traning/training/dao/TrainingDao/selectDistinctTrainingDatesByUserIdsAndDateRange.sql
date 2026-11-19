SELECT DISTINCT user_id, training_date
FROM trainings
WHERE user_id IN /* userIds */(0)
  AND training_date BETWEEN /* startDate */'2026-09-14' AND /* endDate */'2026-10-18'
  AND deleted_at IS NULL
ORDER BY user_id, training_date
