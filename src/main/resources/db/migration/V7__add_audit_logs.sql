-- 監査ログテーブル
-- 手動実行: mysql -u root training_db < V7__add_audit_logs.sql
CREATE TABLE audit_logs (
  id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT        NULL,
  action       VARCHAR(50)   NOT NULL COMMENT 'TRAINING_SAVE / TRAINING_DELETE 等のアクションコード',
  target_table VARCHAR(50)   NOT NULL COMMENT '操作対象テーブル名',
  target_id    BIGINT        NULL     COMMENT '操作対象レコードID。一括操作や新規作成時はNULL',
  ip_address   VARCHAR(45)   NOT NULL COMMENT 'IPv4/IPv6両対応',
  request_path VARCHAR(255)  NOT NULL COMMENT 'リクエストURLのパス部分のみ（クエリパラメータ除外）',
  changed_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  extra        TEXT          NULL     COMMENT 'JSON形式補足情報（現時点では常にNULL）',
  INDEX idx_audit_user       (user_id),
  INDEX idx_audit_changed_at (changed_at),
  INDEX idx_audit_action     (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
