UPDATE goal_proposals
SET status = 'WITHDRAWN', responded_at = /* now */'2026-01-01 00:00:00'
WHERE member_user_id = /* memberUserId */0
  AND status = 'PENDING'
