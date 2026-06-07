SELECT COUNT(id)
FROM trainings
WHERE user_id = /* userId */0
  AND YEAR(training_date) = /* year */2026
  AND MONTH(training_date) = /* month */6
  AND deleted_at IS NULL
