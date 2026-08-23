UPDATE users
SET notification_method = /* notificationMethod */'EMAIL',
    update_Datetime = CURRENT_TIMESTAMP
WHERE id = /* userId */0
