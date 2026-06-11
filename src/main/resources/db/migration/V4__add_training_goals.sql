CREATE TABLE training_goals (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  item_name     VARCHAR(100) NOT NULL,
  target_weight DECIMAL(6,2) NULL COMMENT '目標重量(kg)。NULLは回数のみ目標',
  target_reps   INT NULL         COMMENT '目標回数。NULLは重量のみ目標',
  target_date   DATE NOT NULL    COMMENT '達成目標期日',
  status        ENUM('ACTIVE','ACHIEVED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  memo          VARCHAR(500) NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_user_status (user_id, status)
);
