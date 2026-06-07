SELECT SUM(td.weight * td.reps)
FROM training_details td
JOIN trainings t ON t.id = td.training_id
WHERE t.user_id = /* userId */0
  AND t.training_date BETWEEN /* startDate */'2026-05-26' AND /* endDate */'2026-06-01'
  AND t.deleted_at IS NULL
  AND td.deleted_at IS NULL
