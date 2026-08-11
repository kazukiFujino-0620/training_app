-- usersテーブルへのorganization_id追加（ita1-1 フェーズ2）
-- 既存データは全件デフォルト店舗（organizations.id=2）へ割り当てる
ALTER TABLE users ADD COLUMN organization_id BIGINT NULL COMMENT 'organizations.id を参照';

UPDATE users SET organization_id = 2 WHERE organization_id IS NULL;

ALTER TABLE users MODIFY COLUMN organization_id BIGINT NOT NULL;

ALTER TABLE users ADD INDEX idx_users_organization (organization_id);
