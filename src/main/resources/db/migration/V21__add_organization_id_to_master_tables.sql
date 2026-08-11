-- training_item_master / training_part_master へのorganization_id追加（ita1-1 フェーズ2）
-- 0=オールマイティ（全組織共通）。既存行はDEFAULT 0により自動的に共通種目/部位として扱われる
ALTER TABLE training_item_master ADD COLUMN organization_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=オールマイティ(全組織共通)。organizations.id を参照';
ALTER TABLE training_part_master ADD COLUMN organization_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=オールマイティ(全組織共通)。organizations.id を参照';

-- item_masterのUNIQUE制約を組織単位に拡張する（同名種目を複数組織が独自追加できるようにするため）
ALTER TABLE training_item_master DROP INDEX uq_part_item;
ALTER TABLE training_item_master ADD UNIQUE KEY uq_org_part_item (organization_id, part_code, item_name);

ALTER TABLE training_item_master ADD INDEX idx_item_master_organization (organization_id);
ALTER TABLE training_part_master ADD INDEX idx_part_master_organization (organization_id);
