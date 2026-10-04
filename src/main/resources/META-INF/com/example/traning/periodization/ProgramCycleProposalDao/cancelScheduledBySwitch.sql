UPDATE program_cycle_proposals
SET status = 'CANCELLED_BY_SWITCH'
WHERE trainee_user_id = /* traineeUserId */0
  AND status = 'SCHEDULED'
