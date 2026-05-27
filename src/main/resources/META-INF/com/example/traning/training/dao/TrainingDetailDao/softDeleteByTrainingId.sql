UPDATE training_details
SET deleted_at = NOW()
WHERE training_id = /* trainingId */0
AND deleted_at IS NULL
