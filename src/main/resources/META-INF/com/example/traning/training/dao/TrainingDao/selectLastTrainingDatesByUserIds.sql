SELECT user_id, MAX(training_date) AS training_date
FROM trainings
WHERE user_id IN /* userIds */(0)
  AND training_date <= /* untilDate */'2026-10-18'
  AND deleted_at IS NULL
GROUP BY user_id
