SELECT
    /*%expand*/*
FROM
    trainings
WHERE
    user_id = /* userId */1
AND training_date = /* date */'2026-01-01'
AND deleted_at IS NULL
ORDER BY
    display_order ASC, id ASC
