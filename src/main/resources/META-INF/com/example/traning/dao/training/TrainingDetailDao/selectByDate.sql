SELECT 
  training_details.training_id, 
  training_details.weight, 
  training_details.reps, 
  training_details.set_number,
  trainings.menu
FROM training_details
INNER JOIN
  trainings 
ON
	trainings.id = training_details.training_id
WHERE trainings.training_date = /* date */'2026-05-01'