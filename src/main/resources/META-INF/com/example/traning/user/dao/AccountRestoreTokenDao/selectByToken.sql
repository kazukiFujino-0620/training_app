SELECT
    id,
    user_id,
    token,
    expiry_date
FROM
    account_restore_tokens
WHERE
    token = /* token */'dummy-token'
