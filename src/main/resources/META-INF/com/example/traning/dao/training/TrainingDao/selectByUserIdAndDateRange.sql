SELECT
    training_details.training_id,
    trainings.is_all_completed, 
    trainings.id, 
    trainings.training_date, 
    trainings.part_code, 
    trainings.menu, 
    trainings.memo, 
    trainings.duration, 
    training_details.set_number,
    training_details.weight,
    training_details.reps,
    training_details.count,
    training_details.is_completed
FROM
    trainings
INNER JOIN 
    training_details 
ON
    trainings.id = training_details.training_id
WHERE
    trainings.user_id = /* userId */1
AND
    trainings.training_date BETWEEN /* startDate */'2026-04-27' AND /* endDate */'2026-06-07'
ORDER BY
    trainings.training_date ASC