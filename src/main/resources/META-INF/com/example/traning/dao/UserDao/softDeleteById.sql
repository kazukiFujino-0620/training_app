UPDATE users
SET deleted_at = NOW()
WHERE id = /* userId */0
AND deleted_at IS NULL
