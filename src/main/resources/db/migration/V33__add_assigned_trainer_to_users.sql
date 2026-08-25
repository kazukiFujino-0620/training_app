-- ita4結合試験バグ6対応: 同一トレーニーに複数トレーナーがアドバイスを送信できてしまう問題の修正
-- トレーニーごとに「担当トレーナー」を1人だけ持たせ、以降そのトレーナー以外は送信できないようにする。
-- 既存データは全員NULL（未割り当て）から開始し、各トレーニーへの最初のアドバイス送信時に
-- 送信したトレーナーへ自動的に割り当てる（TrainerAdviceService#send参照）。

ALTER TABLE users ADD COLUMN assigned_trainer_id BIGINT NULL COMMENT '担当トレーナーのusers.id。未割り当て時はNULL';

ALTER TABLE users ADD INDEX idx_users_assigned_trainer (assigned_trainer_id);
