-- audit_logs / withdrawal_requests へのorganization_id追加（ita1-1 フェーズ2、NULL許容）
ALTER TABLE audit_logs ADD COLUMN organization_id BIGINT NULL COMMENT 'organizations.id を参照';
ALTER TABLE withdrawal_requests ADD COLUMN organization_id BIGINT NULL COMMENT 'organizations.id を参照';

-- 対象ユーザーの所属組織からバックフィル（user_idがNULL/削除済み等で判定不能な行はNULLのまま）
UPDATE audit_logs a
JOIN users u ON a.user_id = u.id
SET a.organization_id = u.organization_id
WHERE a.organization_id IS NULL;

UPDATE withdrawal_requests w
JOIN users u ON w.user_id = u.id
SET w.organization_id = u.organization_id
WHERE w.organization_id IS NULL;

ALTER TABLE audit_logs ADD INDEX idx_audit_logs_organization (organization_id);
ALTER TABLE withdrawal_requests ADD INDEX idx_withdrawal_requests_organization (organization_id);
