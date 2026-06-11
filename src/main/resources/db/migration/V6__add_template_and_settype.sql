-- V6: テンプレート機能 + セット種別（WARMUP/MAIN/DROP）
-- このファイルは記録目的のみ。Flyway は未使用のため手動実行が必要。
-- 手動実行: mysql -u root training_db < V6__add_template_and_settype.sql

CREATE TABLE training_templates (
  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT       NOT NULL,
  name         VARCHAR(100) NOT NULL           COMMENT 'テンプレート名（例: "胸の日A"）',
  part_code    VARCHAR(50)  NOT NULL           COMMENT 'メイン部位',
  memo         VARCHAR(500) NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at   DATETIME     NULL               COMMENT '論理削除',
  INDEX idx_template_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE training_template_items (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  template_id   BIGINT       NOT NULL,
  item_name     VARCHAR(100) NOT NULL,
  set_number    INT          NOT NULL           COMMENT 'セット番号（1始まり）',
  set_type      ENUM('WARMUP','MAIN','DROP') NOT NULL DEFAULT 'MAIN',
  weight        DECIMAL(6,2) NULL              COMMENT '目安重量。NULL=都度入力',
  reps          INT          NULL              COMMENT '目安回数。NULL=都度入力',
  display_order INT          NOT NULL DEFAULT 0 COMMENT '種目の表示順',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_template_items_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE training_details
  ADD COLUMN set_type ENUM('WARMUP','MAIN','DROP')
      NOT NULL DEFAULT 'MAIN'
      AFTER reps;
