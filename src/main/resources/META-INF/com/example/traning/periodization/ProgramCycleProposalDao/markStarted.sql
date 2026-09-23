UPDATE program_cycle_proposals
SET status = 'STARTED',
    started_cycle_id = /* startedCycleId */0,
    started_at = /* startedAt */'2026-01-01 00:00:00'
WHERE id = /* id */0
