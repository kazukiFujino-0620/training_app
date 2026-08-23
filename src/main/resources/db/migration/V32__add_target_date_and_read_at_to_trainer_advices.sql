-- ita4-4 追加対応: トレーナーアドバイスに対象日(target_date)と既読状態(read_at)を追加
-- 対象日: トレーナーが送信時に指定するトレーニング実施日。その日のトレーニング詳細画面にも表示するため。
-- 既読: /notices一覧・トレーニング詳細のいずれかで閲覧したら既読とし、ハイライト表示のみを消す
--       （本文自体は「閲覧しても消えない履歴」として引き続き表示し続ける方針は維持、ita4-4結合試験バグ4）

ALTER TABLE trainer_advices ADD COLUMN target_date DATE NULL AFTER target_user_id;
UPDATE trainer_advices SET target_date = DATE(created_at) WHERE target_date IS NULL;
ALTER TABLE trainer_advices MODIFY COLUMN target_date DATE NOT NULL;

ALTER TABLE trainer_advices ADD COLUMN read_at DATETIME NULL COMMENT '既読日時。NULLは未読(ハイライト対象)' AFTER created_at;

ALTER TABLE trainer_advices ADD INDEX idx_trainer_advices_target_user_date (target_user_id, target_date);
