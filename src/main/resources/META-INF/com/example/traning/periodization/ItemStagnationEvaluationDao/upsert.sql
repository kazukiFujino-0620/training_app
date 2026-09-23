INSERT INTO item_stagnation_evaluations (user_id, item_name, level, evaluated_at)
VALUES (/* userId */0, /* itemName */'a', /* level */'NONE', /* evaluatedAt */'2026-01-01 00:00:00')
ON DUPLICATE KEY UPDATE level = VALUES(level), evaluated_at = VALUES(evaluated_at)
