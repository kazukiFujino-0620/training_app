DELETE td FROM training_details td
INNER JOIN trainings t ON td.training_id = t.id
WHERE t.deleted_at IS NOT NULL
AND t.create_datetime < /* cutoff */'2000-01-01 00:00:00'
