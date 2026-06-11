UPDATE training_details
SET deleted_at = NOW()
WHERE id = /* id */0
AND deleted_at IS NULL
