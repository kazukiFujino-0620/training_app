DELETE FROM
    password_reset_tokens
WHERE
    expiry_date <= /* now */'2026-05-01 00:00:00'