SELECT t.part_code, MAX(t.training_date) AS last_date
FROM trainings t
JOIN training_details d ON d.training_id = t.id
WHERE t.user_id = /* userId */0
AND t.training_date <= /* date */'2099-12-31'
AND t.deleted_at IS NULL
AND d.deleted_at IS NULL
AND d.is_completed = 1
AND t.part_code IS NOT NULL
GROUP BY t.part_code
