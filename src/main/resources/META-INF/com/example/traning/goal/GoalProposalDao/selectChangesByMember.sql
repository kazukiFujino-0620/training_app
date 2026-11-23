SELECT id, member_user_id, change_type, old_value, new_value, changed_at
FROM member_goal_changes
WHERE member_user_id = /* memberUserId */0
ORDER BY changed_at DESC, id DESC
