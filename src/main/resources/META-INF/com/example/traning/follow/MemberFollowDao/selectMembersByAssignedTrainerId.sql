select * from users
where assigned_trainer_id = /* trainerId */0
  and role = 'ROLE_USER'
  and deleted_at IS NULL
order by id
