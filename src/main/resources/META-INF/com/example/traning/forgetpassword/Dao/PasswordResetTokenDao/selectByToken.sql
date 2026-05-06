SELECT
    /*%expand*/*
FROM
    password_reset_tokens
WHERE
    token = /* token */'abc'