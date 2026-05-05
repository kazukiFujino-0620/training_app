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
    updated_datetime
FROM
    trainings
ORDER BY
    training_date DESC;