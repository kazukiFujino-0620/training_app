-- 店舗兼任管理テーブル（ita1-1。ROLE_ORG_ADMIN以上が自組織配下の店舗に対してのみ設定可能）
CREATE TABLE user_store_access (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'users.id を参照',
    store_organization_id BIGINT NOT NULL COMMENT 'organizations.id (type=STORE) を参照',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_store_access (user_id, store_organization_id),
    INDEX idx_user_store_access_user (user_id),
    INDEX idx_user_store_access_store (store_organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
