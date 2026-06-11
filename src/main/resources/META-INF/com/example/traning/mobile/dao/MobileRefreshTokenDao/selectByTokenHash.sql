SELECT
  id,
  user_id,
  token_hash,
  device_id,
  expires_at,
  revoked_at,
  created_at
FROM
  mobile_refresh_tokens
WHERE
  token_hash = /* tokenHash */'dummy'
