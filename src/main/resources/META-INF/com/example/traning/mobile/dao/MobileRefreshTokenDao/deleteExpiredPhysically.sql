DELETE mrt FROM mobile_refresh_tokens mrt
INNER JOIN users u ON mrt.user_id = u.id
WHERE u.deleted_at IS NOT NULL
AND u.create_datetime < /* cutoff */'2000-01-01 00:00:00'
