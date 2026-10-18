SELECT id, user_id, week_start_date, target_count, source, program_cycle_id, evaluated, created_at
FROM user_frequency_week_targets
WHERE user_id = /* userId */0
ORDER BY week_start_date DESC
LIMIT 1
