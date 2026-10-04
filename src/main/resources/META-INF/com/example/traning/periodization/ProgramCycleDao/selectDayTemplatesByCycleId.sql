SELECT id, cycle_id, week_number, day_of_week, part_code, template_id, created_at, updated_at
FROM program_cycle_day_templates
WHERE cycle_id = /* cycleId */0
ORDER BY week_number, FIELD(day_of_week, 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')
