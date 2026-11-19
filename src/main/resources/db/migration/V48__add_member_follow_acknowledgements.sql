-- 機能見直し-1-#12 担当会員の一覧と「フォロー推奨」の印

-- 担当になった日時（「記録が1件も無く、担当になってから14日以上」の起点）。既存の担当会員は不明のためNULLのまま
-- （起点は登録日）。担当の割り当て・変更時に UserDao#updateAssignedTrainer で記録し、未割り当てに戻すとNULLにする。
ALTER TABLE users
  ADD COLUMN assigned_trainer_at DATETIME NULL COMMENT '担当トレーナーになった日時。NULL=未割り当て、または不明（V48より前の割り当て）'
  AFTER assigned_trainer_id;

-- 「対応済みにする」の記録（Q12-1・Q12-2）。押した時点で付いていた理由の種類・押した人・日時を残す。
-- 会員ごとに最新の1件で印の表示を判定する（同じ理由が対応から14日続くとまた印を付ける。別の理由はすぐ付ける）。
CREATE TABLE member_follow_acknowledgements (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_user_id BIGINT       NOT NULL COMMENT '対象の会員（users.id）',
  reason_types   VARCHAR(200) NOT NULL COMMENT '押した時点の理由の種類（カンマ区切り。NO_RECENT_RECORD/NO_RECORDS/LOW_ACHIEVEMENT/PAIN_REPORT）',
  acted_by       BIGINT       NOT NULL COMMENT '押したスタッフ（users.id）',
  acted_at       DATETIME     NOT NULL COMMENT '押した日時',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_member_follow_ack_member (member_user_id, acted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
