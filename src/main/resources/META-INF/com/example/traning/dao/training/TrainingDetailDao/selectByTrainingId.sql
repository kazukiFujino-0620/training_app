SELECT
    training_details.id, 
    training_details.training_id,
    training_details.set_number, 
    training_details.weight, 
    training_details.reps, 
    training_details.is_completed, 
    training_details.create_datetime, 
    training_details.updated_datetime, 
    trainings.menu, 
    trainings.is_all_completed
FROM
    training_details
JOIN
    trainings ON training_details.training_id = trainings.id
WHERE
    training_details.training_id = /* trainingId */1
ORDER BY
    set_number ASC