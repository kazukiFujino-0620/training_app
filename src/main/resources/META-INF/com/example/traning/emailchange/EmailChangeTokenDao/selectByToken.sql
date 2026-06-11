SELECT id, user_id, new_email, token, expiry_date, created_at
FROM email_change_tokens
WHERE token = /* token */''
