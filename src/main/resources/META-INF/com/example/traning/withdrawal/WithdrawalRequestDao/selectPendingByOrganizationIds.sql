SELECT
    id,
    user_id,
    reason_type,
    reason_text,
    status,
    requested_at,
    processed_at,
    processed_by,
    created_at,
    updated_at,
    organization_id
FROM withdrawal_requests
WHERE status = 'PENDING'
  AND organization_id IN /* organizationIds */(1, 2)
ORDER BY requested_at ASC
