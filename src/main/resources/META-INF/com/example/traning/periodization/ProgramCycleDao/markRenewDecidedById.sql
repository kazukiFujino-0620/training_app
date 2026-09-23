UPDATE program_cycles
SET renew_decided_at = /* decidedAt */'2026-01-01 00:00:00'
WHERE id = /* id */0
  AND renew_decided_at IS NULL
