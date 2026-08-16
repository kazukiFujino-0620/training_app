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
  AND NOT EXISTS (
    SELECT 1 FROM notice_dismissals nd
    WHERE nd.notice_id = notices.id AND nd.user_id = /* userId */1
  )
ORDER BY
  created_at DESC
