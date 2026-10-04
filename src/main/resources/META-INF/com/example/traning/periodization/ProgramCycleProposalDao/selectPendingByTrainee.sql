SELECT id, trainee_user_id, trainer_user_id, source_preset_program_id, name, total_weeks, status, responded_at, content_updated_at, started_cycle_id, started_at, created_at, updated_at
FROM program_cycle_proposals
WHERE trainee_user_id = /* traineeUserId */0
  AND status = 'PENDING'
ORDER BY id DESC
