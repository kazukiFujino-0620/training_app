-- 機能見直し-1-#4 メニュー自動作成（詳細設計書1章・5-1節）

-- 器具マスタ。種目との紐付け（training_item_equipment）が無い種目は器具で絞り込まない。
CREATE TABLE equipment_master (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  name                VARCHAR(50) NOT NULL COMMENT '例: バーベル, ダンベル, マシン, ケーブル, ベンチ・ラック, 自重',
  category            VARCHAR(30) NOT NULL COMMENT 'FREE_WEIGHT/MACHINE/CABLE/BENCH_RACK/BODYWEIGHT',
  weight_increment_kg DECIMAL(4,2) NULL COMMENT '重量の刻み(kg)。生成時の重量はこの刻みに切り捨てる。NULLは既定値(2.5kg)',
  organization_id     BIGINT NOT NULL DEFAULT 0 COMMENT '0=全ジム共通。ジム固有の設備はそのorganization_id',
  display_order       INT NOT NULL DEFAULT 1,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 種目ごとの必要器具（AND条件: 紐付く全器具が使える場合のみ候補にする）。中身は段階的に投入する（QA Q4-1・Q4-2）。
CREATE TABLE training_item_equipment (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_name    VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs COMMENT 'training_item_master.item_nameと同一表記',
  equipment_id BIGINT NOT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_item_equipment (item_name, equipment_id),
  CONSTRAINT fk_tie_equipment FOREIGN KEY (equipment_id) REFERENCES equipment_master(id)
);

-- ユーザーの自己申告による除外種目（QA Q4-4）。自動解除はせず、再確認の回答でのみ削除する。
CREATE TABLE user_excluded_items (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id                  BIGINT NOT NULL,
  item_name                VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs,
  reason                   VARCHAR(200) NULL COMMENT '自己申告理由（任意）。診断情報ではなくユーザーのメモ',
  created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_confirmed_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最後に「まだ必要」と回答した日時（登録時は登録日時）',
  next_confirmation_due_at DATETIME NOT NULL COMMENT '次に再確認する日時。登録時+14日、「まだ必要」で回答時+28日',
  UNIQUE KEY uq_user_item (user_id, item_name),
  INDEX idx_user_id (user_id)
);

INSERT INTO equipment_master (name, category, weight_increment_kg, organization_id, display_order) VALUES
  ('バーベル', 'FREE_WEIGHT', 2.50, 0, 1),
  ('ダンベル', 'FREE_WEIGHT', 2.00, 0, 2),
  ('マシン', 'MACHINE', 5.00, 0, 3),
  ('ケーブル', 'CABLE', 5.00, 0, 4),
  ('ベンチ・ラック', 'BENCH_RACK', NULL, 0, 5),
  ('自重', 'BODYWEIGHT', NULL, 0, 6);
