SELECT
  id,
  user_id,
  item_name,
  rest_seconds,
  created_at,
  updated_at
FROM user_item_rest_preferences
WHERE user_id = /* userId */0
  AND item_name = /* itemName */''
