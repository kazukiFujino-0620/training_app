SELECT id, user_id, name, total_weeks, start_date, tier, source_preset_id, created_by_trainer_id, status, renew_decided_at, created_at, updated_at
FROM program_cycles
WHERE user_id IN /* userIds */(0)
  AND status = 'ACTIVE'
ORDER BY user_id, id DESC
