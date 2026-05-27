DELETE FROM trainings
WHERE deleted_at IS NOT NULL
AND create_datetime < /* cutoff */'2000-01-01 00:00:00'
