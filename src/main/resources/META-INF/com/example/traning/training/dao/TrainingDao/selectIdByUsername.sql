SELECT
  id
FROM
  users
WHERE
  user_name = /* username */'admin'
AND
  deleted_at IS NULL