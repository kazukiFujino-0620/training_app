select * from organizations
where deleted_at IS NULL
order by type, name
