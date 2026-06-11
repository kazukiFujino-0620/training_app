SELECT
    DATE_FORMAT(MIN(t.training_date), '%Y-%m-%d') AS week_label,
    MAX(td.weight)                                 AS max_weight,
    SUM(td.weight * td.reps)                       AS total_volume
FROM training_details td
JOIN trainings t ON t.id = td.training_id
WHERE t.user_id       = /* userId */0
  AND t.menu          = /* itemName */''
  AND t.training_date BETWEEN /* startDate */'2026-01-01'
                          AND /* endDate */'2026-12-31'
  AND t.deleted_at    IS NULL
  AND td.deleted_at   IS NULL
GROUP BY  YEARWEEK(t.training_date, 3)
ORDER BY  YEARWEEK(t.training_date, 3)
