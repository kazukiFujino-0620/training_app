select
  id,
  email,
  password,
  user_name,
  role,
  enabled
from
  users
where
  deleted_at IS NULL
order by
  id