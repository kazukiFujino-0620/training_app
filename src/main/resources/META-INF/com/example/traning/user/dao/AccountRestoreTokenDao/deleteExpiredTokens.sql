DELETE FROM account_restore_tokens
WHERE expiry_date <= /* now */'2000-01-01 00:00:00'
