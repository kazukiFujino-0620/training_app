SELECT id, user_id, week_start_date, target_count, source, program_cycle_id, evaluated, created_at
FROM user_frequency_week_targets
WHERE user_id IN /* userIds */(0)
  AND week_start_date BETWEEN /* fromWeekStart */'2026-01-05' AND /* toWeekStart */'2026-10-12'
ORDER BY user_id, week_start_date DESC
