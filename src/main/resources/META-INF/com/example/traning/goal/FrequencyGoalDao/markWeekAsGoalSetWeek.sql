UPDATE user_frequency_week_targets
SET target_count = /* targetCount */3,
    source = 'MANUAL',
    program_cycle_id = NULL,
    evaluated = 0
WHERE user_id = /* userId */0
  AND week_start_date = /* weekStartDate */'2026-10-12'
  AND source <> 'PROGRAM'
