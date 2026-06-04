DELETE FROM mobile_refresh_tokens
WHERE
  expires_at < /* now */null
AND revoked_at IS NULL
