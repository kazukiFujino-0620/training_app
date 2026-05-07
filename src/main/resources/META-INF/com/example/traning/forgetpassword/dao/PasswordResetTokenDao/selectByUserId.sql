SELECT
    /*%expand*/*
FROM
    password_reset_tokens
WHERE
    user_id = /* userId */1