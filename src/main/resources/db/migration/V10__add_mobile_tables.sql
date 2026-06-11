-- V10: モバイルアプリ用テーブル（JWT リフレッシュトークン + FCM デバイストークン）
-- このファイルは記録目的のみ。Flyway は未使用のため手動実行が必要。
-- 手動実行: mysql -u root training_db < V10__add_mobile_tables.sql

CREATE TABLE mobile_refresh_tokens (
  id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT        NOT NULL                COMMENT 'users.id を参照',
  token_hash    VARCHAR(255)  NOT NULL                COMMENT 'リフレッシュトークンのBCryptハッシュ',
  device_id     VARCHAR(255)  NOT NULL                COMMENT 'デバイス固有ID（UUID等）',
  expires_at    DATETIME      NOT NULL                COMMENT 'トークン有効期限',
  revoked_at    DATETIME      NULL                    COMMENT 'NULL=有効、NOT NULL=無効化済み',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_mrt_token_hash (token_hash),
  INDEX idx_mrt_user_id    (user_id),
  INDEX idx_mrt_device_id  (device_id),
  INDEX idx_mrt_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mobile_device_tokens (
  id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id        BIGINT        NOT NULL                COMMENT 'users.id を参照',
  device_token   VARCHAR(255)  NOT NULL                COMMENT 'FCMデバイストークン',
  platform       VARCHAR(10)   NOT NULL                COMMENT 'iOS / ANDROID',
  device_id      VARCHAR(255)  NULL                    COMMENT 'mobile_refresh_tokens.device_id と対応',
  is_active      TINYINT(1)    NOT NULL DEFAULT 1       COMMENT '1=有効、0=無効',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_mdt_device_token (device_token),
  INDEX idx_mdt_user_id  (user_id),
  INDEX idx_mdt_platform (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
