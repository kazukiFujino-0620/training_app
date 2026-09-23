UPDATE program_cycles
SET status = 'COMPLETED'
WHERE id = /* id */0
  AND status = 'ACTIVE'
