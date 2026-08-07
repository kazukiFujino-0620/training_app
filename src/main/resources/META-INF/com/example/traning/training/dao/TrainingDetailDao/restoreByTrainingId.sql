UPDATE training_details
SET deleted_at = NULL
WHERE training_id = /* trainingId */0
AND deleted_at IS NOT NULL
