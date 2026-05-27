SELECT
  td.id,
  td.training_id,
  td.set_number,
  td.weight,
  td.reps,
  td.count,
  td.is_completed,
  td.create_datetime,
  td.updated_datetime,
  t.menu
FROM
  training_details td
INNER JOIN
  trainings t
ON
  t.id = td.training_id
WHERE
  t.training_date = /* date */'2026-05-01'
AND
  t.deleted_at IS NULL
AND
  td.deleted_at IS NULL
ORDER BY
  td.training_id ASC,
  td.set_number ASC