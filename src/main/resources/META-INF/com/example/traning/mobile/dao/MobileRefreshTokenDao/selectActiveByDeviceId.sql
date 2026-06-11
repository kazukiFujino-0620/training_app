SELECT *
FROM mobile_refresh_tokens
WHERE device_id = /* deviceId */'dummy'
  AND revoked_at IS NULL
  AND expires_at > NOW()
