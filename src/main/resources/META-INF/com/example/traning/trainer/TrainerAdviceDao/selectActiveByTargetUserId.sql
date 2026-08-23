select * from trainer_advices
where target_user_id = /* targetUserId */1
  and deleted_at is null
order by created_at desc
