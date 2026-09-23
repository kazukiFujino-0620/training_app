SELECT
    t.training_date            AS training_date,
    MAX(td.weight)             AS max_weight,
    SUM(td.weight * td.reps)   AS total_volume
FROM training_details td
JOIN trainings t ON t.id = td.training_id
WHERE t.user_id       = /* userId */0
  AND t.menu          = /* itemName */''
  AND t.deleted_at    IS NULL
  AND td.deleted_at   IS NULL
  AND td.is_completed = 1
  AND td.set_type    <> 'WARMUP'
GROUP BY t.training_date
ORDER BY t.training_date DESC
LIMIT /* limit */4
