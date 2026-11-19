-- 機能見直し-1-#12: 担当になった日時。MySQLのSETは左から順に評価されるため、assigned_trainer_idを書き換える前に
-- 変更前の値と比べる（同じトレーナーへの再設定では日時を変えない。未割り当てに戻すとNULL）
UPDATE users
SET assigned_trainer_at = CASE
      WHEN /* assignedTrainerId */0 IS NULL THEN NULL
      WHEN assigned_trainer_id <=> /* assignedTrainerId */0 THEN assigned_trainer_at
      ELSE CURRENT_TIMESTAMP
    END,
    assigned_trainer_id = /* assignedTrainerId */0,
    update_Datetime = CURRENT_TIMESTAMP
WHERE id = /* userId */0
