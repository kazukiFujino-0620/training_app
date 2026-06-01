SELECT
  id,
  template_id,
  item_name,
  set_number,
  set_type,
  weight,
  reps,
  display_order,
  created_at,
  updated_at
FROM training_template_items
WHERE template_id = /* templateId */0
ORDER BY display_order ASC, set_number ASC
