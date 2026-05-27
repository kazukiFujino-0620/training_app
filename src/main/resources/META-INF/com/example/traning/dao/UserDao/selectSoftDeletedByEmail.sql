select * from users
where email = /* email */'test@example.com'
and deleted_at IS NOT NULL
