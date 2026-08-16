SELECT
  id,
  organization_id,
  title,
  body,
  created_by,
  created_at,
  deleted_at
FROM
  notices
WHERE
  id = /* id */1
  AND deleted_at IS NULL
