-- ita3-1: ヘルスケア連動対応（HealthKit/Health Connect、読み取りのみ）
-- 対象データ: 体重（既存body_measurementsを拡張）・歩数・心拍数・消費カロリー・睡眠

ALTER TABLE body_measurements
  ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/HEALTHKIT/HEALTH_CONNECT';

CREATE TABLE health_steps (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  record_date DATE NOT NULL,
  step_count INT NOT NULL,
  source VARCHAR(20) NOT NULL COMMENT 'HEALTHKIT/HEALTH_CONNECT',
  synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_health_steps_user_date_source (user_id, record_date, source),
  KEY idx_health_steps_user_id (user_id)
);

CREATE TABLE health_heart_rate (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  record_date DATE NOT NULL,
  avg_bpm INT NULL,
  min_bpm INT NULL,
  max_bpm INT NULL,
  source VARCHAR(20) NOT NULL COMMENT 'HEALTHKIT/HEALTH_CONNECT',
  synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_health_heart_rate_user_date_source (user_id, record_date, source),
  KEY idx_health_heart_rate_user_id (user_id)
);

CREATE TABLE health_calories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  record_date DATE NOT NULL,
  active_calories DECIMAL(7,2) NULL,
  total_calories DECIMAL(7,2) NULL,
  source VARCHAR(20) NOT NULL COMMENT 'HEALTHKIT/HEALTH_CONNECT',
  synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_health_calories_user_date_source (user_id, record_date, source),
  KEY idx_health_calories_user_id (user_id)
);

CREATE TABLE health_sleep (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  sleep_date DATE NOT NULL COMMENT '起床日基準の睡眠日付',
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  duration_minutes INT NOT NULL,
  source VARCHAR(20) NOT NULL COMMENT 'HEALTHKIT/HEALTH_CONNECT',
  synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_health_sleep_user_date_source (user_id, sleep_date, source),
  KEY idx_health_sleep_user_id (user_id)
);
