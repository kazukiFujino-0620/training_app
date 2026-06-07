CREATE TABLE body_measurements (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  measured_date   DATE NOT NULL,
  weight_kg       DECIMAL(5,2) NOT NULL,
  body_fat_pct    DECIMAL(4,1) NULL,
  memo            VARCHAR(200) NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date (user_id, measured_date),
  INDEX idx_user_date (user_id, measured_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
