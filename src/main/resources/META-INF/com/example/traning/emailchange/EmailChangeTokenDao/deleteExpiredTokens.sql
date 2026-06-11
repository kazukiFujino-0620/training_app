DELETE FROM email_change_tokens
WHERE expiry_date <= /* now */'2026-05-01 00:00:00'
