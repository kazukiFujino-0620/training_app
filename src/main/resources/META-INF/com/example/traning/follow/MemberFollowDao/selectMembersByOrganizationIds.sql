select * from users
where organization_id IN /* organizationIds */(0)
  and role = 'ROLE_USER'
  and deleted_at IS NULL
order by id
