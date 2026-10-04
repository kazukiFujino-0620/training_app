SELECT id, cycle_id, week_number, target_intensity_pct, is_deload, created_at
FROM program_cycle_weeks
WHERE cycle_id = /* cycleId */0
ORDER BY week_number
