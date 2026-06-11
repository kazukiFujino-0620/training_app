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
    update_datetime,
    height_cm,
    weight_kg,
    gender,
    birth_date
FROM
    users
WHERE
    email = /* email */'placeholder'
AND
    deleted_at IS NULL
