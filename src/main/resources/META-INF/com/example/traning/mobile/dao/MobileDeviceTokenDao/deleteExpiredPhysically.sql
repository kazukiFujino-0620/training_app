DELETE mdt FROM mobile_device_tokens mdt
INNER JOIN users u ON mdt.user_id = u.id
WHERE u.deleted_at IS NOT NULL
AND u.create_datetime < /* cutoff */'2000-01-01 00:00:00'
