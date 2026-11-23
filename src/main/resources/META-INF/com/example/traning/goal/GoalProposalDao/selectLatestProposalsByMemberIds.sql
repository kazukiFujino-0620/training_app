SELECT p.id, p.member_user_id, p.proposer_user_id, p.weekly_target_count, p.goal_mode, p.comment,
       p.next_review_date, p.status, p.proposed_at, p.responded_at, NULL AS proposer_name
FROM goal_proposals p
WHERE p.member_user_id IN /* memberIds */(0)
  AND NOT EXISTS (
    SELECT 1 FROM goal_proposals newer
    WHERE newer.member_user_id = p.member_user_id
      AND (newer.proposed_at > p.proposed_at OR (newer.proposed_at = p.proposed_at AND newer.id > p.id))
  )
