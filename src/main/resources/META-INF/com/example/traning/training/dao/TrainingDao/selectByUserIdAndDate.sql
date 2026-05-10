SELECT
    /*%expand*/*
FROM
    trainings
WHERE
    user_id = /* userId */1
AND training_date = /* date */'2026-01-01'
ORDER BY
    id ASC
