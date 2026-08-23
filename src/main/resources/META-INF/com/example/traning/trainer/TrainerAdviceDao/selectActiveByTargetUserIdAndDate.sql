select * from trainer_advices
where target_user_id = /* targetUserId */1
  and target_date = /* targetDate */'2026-01-01'
  and deleted_at is null
order by created_at desc
