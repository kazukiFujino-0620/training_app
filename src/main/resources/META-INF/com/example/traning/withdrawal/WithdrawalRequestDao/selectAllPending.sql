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
    updated_at
FROM withdrawal_requests
WHERE status = 'PENDING'
ORDER BY requested_at ASC
