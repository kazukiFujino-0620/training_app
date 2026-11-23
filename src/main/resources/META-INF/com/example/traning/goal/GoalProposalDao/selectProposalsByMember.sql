SELECT p.id, p.member_user_id, p.proposer_user_id, p.weekly_target_count, p.goal_mode, p.comment,
       p.next_review_date, p.status, p.proposed_at, p.responded_at, u.user_Name AS proposer_name
FROM goal_proposals p
LEFT JOIN users u ON u.id = p.proposer_user_id
WHERE p.member_user_id = /* memberUserId */0
ORDER BY p.proposed_at DESC, p.id DESC
