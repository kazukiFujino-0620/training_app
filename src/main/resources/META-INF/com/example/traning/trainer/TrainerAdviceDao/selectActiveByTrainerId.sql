select * from trainer_advices
where trainer_id = /* trainerId */1
  and deleted_at is null
order by created_at desc
