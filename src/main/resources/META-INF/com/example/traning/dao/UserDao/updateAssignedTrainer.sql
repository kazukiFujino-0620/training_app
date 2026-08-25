UPDATE users
SET assigned_trainer_id = /* assignedTrainerId */0,
    update_Datetime = CURRENT_TIMESTAMP
WHERE id = /* userId */0
