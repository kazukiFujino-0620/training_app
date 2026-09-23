SELECT id, user_id, item_name, level, evaluated_at
FROM item_stagnation_evaluations
WHERE user_id = /* userId */0
  AND item_name IN /* itemNames */('a')
