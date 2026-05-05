SELECT
    trainings.id,
    trainings.is_all_completed,
    trainings.user_id,
    trainings.training_date,
    trainings.part_code,
    trainings.menu,
    trainings.memo,
    trainings.duration,
    trainings.create_datetime,
    trainings.updated_datetime
FROM
    trainings
WHERE
    trainings.user_id = /* userId */1
AND
    trainings.training_date BETWEEN /* startDate */'2026-04-27' AND /* endDate */'2026-06-07'
ORDER BY
    trainings.training_date ASC,
    trainings.id ASC