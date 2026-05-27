-- アカウント復元トークンテーブル
-- 論理削除済みアカウントの復元フローで使用する
CREATE TABLE account_restore_tokens (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    token       VARCHAR(255) NOT NULL,
    expiry_date DATETIME     NOT NULL,
    UNIQUE KEY uq_restore_token (token),
    INDEX idx_restore_user_id (user_id)
);
