-- 機能見直し-1-#2: 種目ライブラリのフォーム解説（複合種目・画像動画＋注意事項）
-- 対象は複合種目Tier1想定8種目のみ（item_name文字列キーでtraining_item_masterと緩く紐付け。
-- user_item_rest_preferences(V38)と同じ既存パターンを踏襲）。
--
-- 本マイグレーションはスキーマ作成のみ。画像/動画の実素材・注意事項テキストは
-- 別途データ投入マイグレーションで追加する（実素材準備・専門的根拠の確認が別タスクのため）。

CREATE TABLE training_item_form_guides (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_name   VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs
              COMMENT '種目名。training_item_master.item_nameと同一表記で紐付け',
  image_url   VARCHAR(500) NULL COMMENT '/images/exercise-guides/配下の相対パスまたは絶対URL',
  video_url   VARCHAR(500) NULL COMMENT '外部動画URL（YouTube限定公開等を想定）',
  joint_angle_note VARCHAR(200) NULL
              COMMENT '関節角度線に添える注記。「一般的な目安」等、断定を避ける文言を必須とする（会議確定）',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_item_name (item_name)
);

CREATE TABLE training_item_form_cautions (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_name     VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs
                COMMENT '種目名。training_item_master.item_nameと同一表記で紐付け',
  display_order INT NOT NULL DEFAULT 1,
  title         VARCHAR(100) NOT NULL COMMENT 'よくある誤りの通称',
  description   VARCHAR(300) NOT NULL COMMENT '現象説明',
  reason        VARCHAR(300) NOT NULL COMMENT '一般的に指摘されている理由（中立的な言い回しで記載）',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_item_name (item_name, display_order)
);
