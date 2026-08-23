-- トレーナーアドバイス機能（ita4-4 (A)）
-- トレーナーが担当トレーニーへ送る、特定のトレーニング記録に紐付かない時系列・自由記述のメッセージ（C案で確定）。
-- お知らせ（notices）とは別テーブルとして管理し、トレーニー側の/notices画面で統合表示する。

CREATE TABLE trainer_advices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trainer_id BIGINT NOT NULL COMMENT '送信したトレーナーのusers.id',
    target_user_id BIGINT NOT NULL COMMENT '宛先トレーニーのusers.id',
    body VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL COMMENT '論理削除。取り下げ時に設定',
    INDEX idx_trainer_advices_target_user (target_user_id),
    INDEX idx_trainer_advices_trainer (trainer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
