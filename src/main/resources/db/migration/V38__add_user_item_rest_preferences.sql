CREATE TABLE user_item_rest_preferences (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  item_name    VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs COMMENT '種目名。training_item_master.item_nameと同一表記で紐付け',
  rest_seconds INT NOT NULL COMMENT 'ユーザーが登録した希望レスト時間(秒)',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_item (user_id, item_name),
  INDEX idx_user_id (user_id)
);
