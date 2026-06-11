SELECT
  id,
  user_id,
  name,
  part_code,
  memo,
  created_at,
  updated_at,
  deleted_at
FROM training_templates
WHERE id = /* id */0
AND deleted_at IS NULL
