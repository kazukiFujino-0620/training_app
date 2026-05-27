SELECT
    /*%expand*/*
FROM
    users
WHERE
/*%if userName != null && userName != "" */
    user_Name LIKE /* userName */'dummy'
AND
/*%end*/
    deleted_at IS NULL
ORDER BY id ASC