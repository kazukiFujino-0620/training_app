UPDATE program_cycle_proposals
SET status = 'SUPERSEDED'
WHERE trainee_user_id = /* traineeUserId */0
  AND status = 'PENDING'
