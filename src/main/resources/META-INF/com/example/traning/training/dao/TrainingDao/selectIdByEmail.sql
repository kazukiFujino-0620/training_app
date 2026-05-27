SELECT
  id
FROM
  users
WHERE
  email = /* email */'admin@example.com'
AND
  deleted_at IS NULL
