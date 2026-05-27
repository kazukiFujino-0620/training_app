UPDATE users
SET deleted_at = NULL
WHERE id = /* userId */0
AND deleted_at IS NOT NULL
