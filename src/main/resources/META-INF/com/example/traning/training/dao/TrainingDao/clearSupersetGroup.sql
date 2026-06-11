UPDATE trainings
SET
  superset_group_id = null,
  updated_datetime = /* updatedDatetime */'2026-01-01 00:00:00'
WHERE
  superset_group_id = /* supersetGroupId */0
AND deleted_at IS NULL
