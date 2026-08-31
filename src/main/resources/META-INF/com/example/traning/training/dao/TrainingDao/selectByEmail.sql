SELECT
    id,
    email,
    password,
    user_name,
    role,
    enabled,
    google_id,
    line_id,
    create_datetime,
    update_datetime,
    height_cm,
    weight_kg,
    gender,
    birth_date,
    current_goal_mode,
    organization_id,
    notification_method,
    line_friend_added,
    assigned_trainer_id,
    ai_advice_consent
FROM
    users
WHERE
    email = /* email */'placeholder'
AND
    deleted_at IS NULL
