SELECT i.id, i.day_template_id, i.item_name, i.display_order, i.target_sets
FROM preset_program_day_template_items i
JOIN preset_program_day_templates d ON d.id = i.day_template_id
WHERE d.preset_program_id = /* presetProgramId */0
ORDER BY i.day_template_id, i.display_order, i.id
