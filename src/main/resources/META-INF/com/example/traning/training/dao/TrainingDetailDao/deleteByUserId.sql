DELETE FROM training_details
WHERE training_id IN (
    SELECT id FROM trainings WHERE user_id = /* userId */0
)
