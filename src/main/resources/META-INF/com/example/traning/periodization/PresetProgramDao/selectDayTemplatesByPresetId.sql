SELECT id, preset_program_id, week_number, day_of_week, part_code
FROM preset_program_day_templates
WHERE preset_program_id = /* presetProgramId */0
ORDER BY week_number, FIELD(day_of_week, 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')
