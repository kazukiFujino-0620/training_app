SELECT
    training_details.id,
    training_details.training_id,
    training_details.set_number,
    training_details.weight,
    training_details.reps,
    training_details.set_type,
    training_details.count,
    training_details.is_completed,
    training_details.create_datetime,
    training_details.updated_datetime
FROM
    training_details
WHERE
    training_details.training_id = /* trainingId */1
AND
    training_details.deleted_at IS NULL
ORDER BY
    set_number ASC