SELECT
    id,
    email,
    password,
    user_name,
    role,
    enabled,
    google_id,
    line_id,
    create_datetime,
    update_datetime
FROM
    users
WHERE
    user_name = /* userName */'placeholder'
AND
    deleted_at IS NULL
