SELECT id, day_template_id, item_name, display_order, target_sets
FROM program_cycle_day_template_items
WHERE day_template_id = /* dayTemplateId */0
ORDER BY display_order, id
