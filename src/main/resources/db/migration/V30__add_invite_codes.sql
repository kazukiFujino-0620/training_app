-- 招待コード管理テーブル（ita1-1 未実施分・ita3-3 招待コード方式の発行・管理基盤）
-- 発行者（ADMIN/ORG_ADMIN）が組織向けに発行し、登録LP（ita3-3）で入力されると
-- organization_idへの自動割り当てに使用する。

CREATE TABLE invite_codes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL COMMENT '招待コード文字列。QR/URL直接入力どちらでも使用',
    organization_id BIGINT NOT NULL COMMENT 'organizations.id (GYM/STORE) を参照。このコードで登録すると割り当てられる組織',
    expires_at DATETIME NULL COMMENT '有効期限。NULLは無期限',
    max_uses INT NULL COMMENT '使用回数上限。NULLは無制限',
    used_count INT NOT NULL DEFAULT 0 COMMENT '現在の使用回数',
    created_by BIGINT NOT NULL COMMENT '発行した管理者のusers.id',
    revoked_at DATETIME NULL COMMENT '失効日時。NULLなら有効',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_invite_codes_code (code),
    INDEX idx_invite_codes_organization (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
