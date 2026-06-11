SELECT
    id,
    user_id,
    item_name,
    max_weight,
    max_reps,
    achieved_date,
    created_at,
    updated_at
FROM personal_records
WHERE user_id   = /* userId */0
  AND item_name = /* itemName */''
