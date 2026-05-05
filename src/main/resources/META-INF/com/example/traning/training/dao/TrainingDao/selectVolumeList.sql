SELECT
    DATE_FORMAT(t.training_date, '%Y-%m-%d') AS training_date,
    SUM(d.weight * d.reps) AS total_volume
FROM trainings t
JOIN training_details d ON t.id = d.training_id
WHERE t.user_id = /* userId */5
  AND t.part_code = /* partCode */'LEG'
  AND t.training_date BETWEEN /* startDate */'2026-05-01' AND /* endDate */'2026-05-07'
GROUP BY t.training_date
ORDER BY training_date ASC;