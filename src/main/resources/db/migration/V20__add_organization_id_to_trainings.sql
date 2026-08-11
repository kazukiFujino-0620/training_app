-- trainingsテーブルへのorganization_id追加（ita1-1 フェーズ2、非正規化）
-- users.organization_id からコピーする（V19適用後の実行を前提とする）
ALTER TABLE trainings ADD COLUMN organization_id BIGINT NULL COMMENT 'organizations.id を参照（usersからの非正規化）';

UPDATE trainings t
JOIN users u ON t.user_id = u.id
SET t.organization_id = u.organization_id
WHERE t.organization_id IS NULL;

ALTER TABLE trainings MODIFY COLUMN organization_id BIGINT NOT NULL;

ALTER TABLE trainings ADD INDEX idx_trainings_organization (organization_id);
