UPDATE goal_proposals
SET status = /* status */'ACCEPTED', responded_at = /* now */'2026-01-01 00:00:00'
WHERE id = /* id */0
  AND status = 'PENDING'
