UPDATE mobile_refresh_tokens
SET
  revoked_at = /* revokedAt */null
WHERE
  token_hash = /* tokenHash */'dummy'
