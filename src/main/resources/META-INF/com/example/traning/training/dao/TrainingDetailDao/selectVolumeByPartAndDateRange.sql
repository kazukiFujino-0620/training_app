SELECT t.part_code AS part_code, SUM(td.weight * td.reps) AS total_volume
FROM training_details td
JOIN trainings t ON t.id = td.training_id
WHERE t.user_id = /* userId */0
  AND t.training_date BETWEEN /* startDate */'2026-05-26' AND /* endDate */'2026-06-01'
  AND t.deleted_at IS NULL
  AND td.deleted_at IS NULL
  AND td.is_completed = 1
  AND td.set_type <> 'WARMUP'
GROUP BY t.part_code
ORDER BY t.part_code
