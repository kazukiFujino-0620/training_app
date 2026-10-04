SELECT id, preset_program_id, week_number, target_intensity_pct, is_deload
FROM preset_program_weeks
WHERE preset_program_id = /* presetProgramId */0
ORDER BY week_number
