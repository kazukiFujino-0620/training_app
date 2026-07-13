UPDATE trainings
SET
    is_all_completed = /* isAllCompleted */false,
    updated_datetime = /* updatedDatetime */'2026-01-01 00:00:00'
    /*%if duration != null */
    , duration       = /* duration */'00:00:00'
    /*%end*/
WHERE id = /* id */0
