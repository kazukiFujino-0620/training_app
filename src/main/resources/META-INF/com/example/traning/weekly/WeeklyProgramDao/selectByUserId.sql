SELECT
    id,
    user_id,
    day_of_week,
    part_code,
    template_id,
    created_at,
    updated_at
FROM weekly_programs
WHERE user_id = /* userId */0
ORDER BY FIELD(day_of_week, 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')
