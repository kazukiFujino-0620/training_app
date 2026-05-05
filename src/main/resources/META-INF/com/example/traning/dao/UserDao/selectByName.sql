SELECT
    /*%expand*/*
FROM
    users
WHERE
/*%if userName != null && userName != "" */
    user_Name LIKE /* userName */'dummy'
/*%end*/
ORDER BY id ASC