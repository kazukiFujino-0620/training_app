select
  id,
  email,
  password,
  user_name,
  role,
  enabled,
  create_datetime,
  deleted_at,
  organization_id
from
  users
where
  deleted_at IS NOT NULL
order by
  deleted_at desc
