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
  organization_id = /* organizationId */1
  AND deleted_at IS NULL
ORDER BY
  created_at DESC
