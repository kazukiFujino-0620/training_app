SELECT
    id,
    is_all_completed,
    user_id,
    training_date,
    part_code,
    menu,
    memo,
    duration,
    create_datetime,
    updated_datetime,
    deleted_at
FROM
    trainings
WHERE
    deleted_at IS NOT NULL
ORDER BY
    deleted_at DESC
