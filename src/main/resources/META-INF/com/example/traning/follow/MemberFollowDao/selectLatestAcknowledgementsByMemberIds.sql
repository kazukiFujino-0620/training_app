SELECT a.id, a.member_user_id, a.reason_types, a.acted_by, a.acted_at, u.user_Name AS acted_by_name
FROM member_follow_acknowledgements a
LEFT JOIN users u ON u.id = a.acted_by
WHERE a.member_user_id IN /* memberIds */(0)
  AND NOT EXISTS (
    SELECT 1 FROM member_follow_acknowledgements newer
    WHERE newer.member_user_id = a.member_user_id
      AND (newer.acted_at > a.acted_at OR (newer.acted_at = a.acted_at AND newer.id > a.id))
  )
