SELECT id, user_id, item_name, reason, created_at, last_confirmed_at, next_confirmation_due_at
FROM user_excluded_items
WHERE user_id IN /* userIds */(0)
  AND reason IS NOT NULL
  AND TRIM(reason) <> ''
  AND created_at >= /* since */'2026-11-05 00:00:00'
ORDER BY user_id, created_at
