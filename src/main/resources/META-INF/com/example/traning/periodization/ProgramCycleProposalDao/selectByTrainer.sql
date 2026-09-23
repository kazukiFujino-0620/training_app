SELECT id, trainee_user_id, trainer_user_id, preset_program_id, status, responded_at, started_cycle_id, started_at, created_at, updated_at
FROM program_cycle_proposals
WHERE trainer_user_id = /* trainerUserId */0
ORDER BY id DESC
