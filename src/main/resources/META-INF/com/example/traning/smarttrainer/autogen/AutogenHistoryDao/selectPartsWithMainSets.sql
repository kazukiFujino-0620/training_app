SELECT DISTINCT t.part_code
FROM trainings t
JOIN training_details d ON d.training_id = t.id
WHERE t.user_id = /* userId */0
AND t.training_date BETWEEN /* from */'2000-01-01' AND /* to */'2099-12-31'
AND t.deleted_at IS NULL
AND d.deleted_at IS NULL
AND d.is_completed = 1
AND d.set_type = 'MAIN'
AND t.part_code IS NOT NULL
