-- 2要素認証設定テーブル
CREATE TABLE user_mfa_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT 'users.id を参照',
    secret_key VARCHAR(64) NOT NULL,
    is_enabled TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_mfa_settings_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- バックアップコードテーブル
CREATE TABLE mfa_backup_codes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'users.id を参照',
    code_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt ハッシュ',
    is_used TINYINT(1) NOT NULL DEFAULT 0,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mfa_backup_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
