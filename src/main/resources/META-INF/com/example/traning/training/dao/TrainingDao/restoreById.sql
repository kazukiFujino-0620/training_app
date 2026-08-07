UPDATE trainings
SET deleted_at = NULL
WHERE id = /* id */0
AND deleted_at IS NOT NULL
