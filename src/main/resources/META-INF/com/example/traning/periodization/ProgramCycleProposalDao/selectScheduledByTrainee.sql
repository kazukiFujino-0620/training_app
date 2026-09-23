SELECT id, trainee_user_id, trainer_user_id, preset_program_id, status, responded_at, started_cycle_id, started_at, created_at, updated_at
FROM program_cycle_proposals
WHERE trainee_user_id = /* traineeUserId */0
  AND status = 'SCHEDULED'
ORDER BY responded_at, id
