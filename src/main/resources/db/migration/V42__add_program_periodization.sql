-- 機能見直し-1-#3: 期分け構造を持つ事前構築プログラム
-- 詳細設計書「機能見直し-1-#3 - 期分け構造を持つ事前構築プログラム_設計書」1章（設計書上はV41だが、
-- V41は#2で使用済みのためV42で採番）。既存のweekly_programsは変更しない（新規テーブル方式、QA Q3-1）。
--
-- preset_programsはtraining_item_masterと同じくorganization_idを持つ（0=全組織共通。USER回答によりB方式で確定）。
-- 本マイグレーションはスキーマ作成のみ。プリセットの中身（週別強度・曜日構成・種目リスト）は
-- training-coordinatorが別途作成中のため、決定後に別マイグレーションで投入する（QA Q3-2）。

-- ===== 既定プログラム（プリセット）マスタ: 運営側が用意する読み取り専用データ =====
CREATE TABLE preset_programs (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id  BIGINT NOT NULL DEFAULT 0 COMMENT '0=オールマイティ(全組織共通)。organizations.id を参照',
  name             VARCHAR(100) NOT NULL,
  purpose_category VARCHAR(30) NOT NULL COMMENT 'BULK/CUT/MAINTENANCE/STRENGTH等',
  total_weeks      INT NOT NULL DEFAULT 4,
  description      VARCHAR(500) NULL,
  display_order    INT NOT NULL DEFAULT 1,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_preset_programs_organization (organization_id, display_order)
);

CREATE TABLE preset_program_weeks (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  preset_program_id    BIGINT NOT NULL,
  week_number          INT NOT NULL COMMENT '1始まり',
  target_intensity_pct DECIMAL(4,1) NOT NULL COMMENT '目標強度(%1RM)。例: 70.0',
  is_deload            TINYINT(1) NOT NULL DEFAULT 0,
  UNIQUE KEY uq_preset_week (preset_program_id, week_number),
  CONSTRAINT fk_ppw_preset FOREIGN KEY (preset_program_id) REFERENCES preset_programs(id)
);

CREATE TABLE preset_program_day_templates (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  preset_program_id BIGINT NOT NULL,
  week_number       INT NOT NULL,
  day_of_week       ENUM('MON','TUE','WED','THU','FRI','SAT','SUN') NOT NULL,
  part_code         VARCHAR(20) NULL,
  UNIQUE KEY uq_preset_week_day (preset_program_id, week_number, day_of_week),
  CONSTRAINT fk_ppdt_preset FOREIGN KEY (preset_program_id) REFERENCES preset_programs(id)
);

-- QA Q3-3（種目リストまでプリセットに固定）
CREATE TABLE preset_program_day_template_items (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  day_template_id  BIGINT NOT NULL,
  item_name        VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs
                   COMMENT '種目名。training_item_master.item_nameと同一表記で紐付け',
  display_order    INT NOT NULL DEFAULT 1,
  target_sets      INT NOT NULL DEFAULT 3,
  INDEX idx_ppdti_day_template (day_template_id, display_order),
  CONSTRAINT fk_ppdti_day_template FOREIGN KEY (day_template_id) REFERENCES preset_program_day_templates(id)
);

-- ===== ユーザーが実際に採用している期分けサイクル =====
CREATE TABLE program_cycles (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id               BIGINT NOT NULL,
  name                  VARCHAR(100) NOT NULL,
  total_weeks           INT NOT NULL DEFAULT 4,
  start_date            DATE NOT NULL,
  tier                  VARCHAR(30) NOT NULL
                        COMMENT 'BEGINNER_PRESET(プリセット採用。採用後に編集しても変わらない)/INTERMEDIATE_CUSTOM(白紙から自作)/TRAINER_MANAGED(トレーナー作成)',
  source_preset_id      BIGINT NULL COMMENT '採用元プリセットID。白紙から自作した場合はNULL',
  created_by_trainer_id BIGINT NULL COMMENT 'TRAINER_MANAGEDの場合の作成トレーナーuser_id',
  status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/COMPLETED/ARCHIVED',
  renew_decided_at      DATETIME NULL
                        COMMENT 'サイクル終了後の次の行き先(renewCycleの3択)を選んだ日時。NULLかつCOMPLETEDなら選択待ち（QA Q3-7追記）',
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_status (user_id, status)
);

CREATE TABLE program_cycle_weeks (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  cycle_id              BIGINT NOT NULL,
  week_number           INT NOT NULL COMMENT '1始まり',
  target_intensity_pct  DECIMAL(4,1) NOT NULL COMMENT '目標強度(%1RM)。例: 70.0',
  is_deload             TINYINT(1) NOT NULL DEFAULT 0,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cycle_week (cycle_id, week_number),
  CONSTRAINT fk_pcw_cycle FOREIGN KEY (cycle_id) REFERENCES program_cycles(id)
);

CREATE TABLE program_cycle_day_templates (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  cycle_id     BIGINT NOT NULL,
  week_number  INT NOT NULL,
  day_of_week  ENUM('MON','TUE','WED','THU','FRI','SAT','SUN') NOT NULL,
  part_code    VARCHAR(20) NULL,
  template_id  BIGINT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_cycle_week_day (cycle_id, week_number, day_of_week),
  CONSTRAINT fk_pcdt_cycle FOREIGN KEY (cycle_id) REFERENCES program_cycles(id)
);

-- QA Q3-3: 採用時にpreset_program_day_template_itemsをコピーし、以降は独立して編集する
CREATE TABLE program_cycle_day_template_items (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  day_template_id  BIGINT NOT NULL,
  item_name        VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs
                   COMMENT '種目名。training_item_master.item_nameと同一表記で紐付け',
  display_order    INT NOT NULL DEFAULT 1,
  target_sets      INT NOT NULL DEFAULT 3,
  INDEX idx_pcdti_day_template (day_template_id, display_order),
  CONSTRAINT fk_pcdti_day_template FOREIGN KEY (day_template_id) REFERENCES program_cycle_day_templates(id)
);

-- QA Q3-8: サイクル採用/作成時点の種目別推定1RMスナップショット（成長グラフの計画値ライン用）
CREATE TABLE program_cycle_item_baselines (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  cycle_id          BIGINT NOT NULL,
  item_name         VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs,
  baseline_one_rm   DECIMAL(6,1) NOT NULL COMMENT 'サイクル採用/作成時点でのOneRmPredictionServiceによる推定1RM(kg)',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_pcib_cycle_item (cycle_id, item_name),
  CONSTRAINT fk_pcib_cycle FOREIGN KEY (cycle_id) REFERENCES program_cycles(id)
);
