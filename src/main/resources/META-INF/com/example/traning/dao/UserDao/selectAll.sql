select
  id,
  email,
  password,
  user_name,
  role,
  enabled,
  organization_id
from
  users
where
  deleted_at IS NULL
order by
  id