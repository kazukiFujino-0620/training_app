select * from users
where deleted_at IS NULL
order by id
