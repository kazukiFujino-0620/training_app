-- 組織・店舗テーブル（2階層マルチテナント化の基盤、ita1-1）
CREATE TABLE organizations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(10) NOT NULL COMMENT 'ALL(全組織共通の予約行) / GYM(組織) / STORE(店舗)',
    parent_organization_id BIGINT NULL COMMENT 'STOREの場合、所属するGYMのid。GYM/ALLはNULL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    INDEX idx_organizations_parent (parent_organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- id=0: オールマイティ（全組織共通。training_item_master/training_part_masterの共通マスタ用の予約行、UNIQUE制約をNULLなしで機能させるため）
-- id=1,2: 既存データの移行先となるデフォルト組織・デフォルト店舗
SET @old_sql_mode = @@sql_mode;
SET sql_mode = CONCAT(@@sql_mode, ',NO_AUTO_VALUE_ON_ZERO');

INSERT INTO organizations (id, name, type, parent_organization_id) VALUES
    (0, 'オールマイティ', 'ALL', NULL),
    (1, 'デフォルト組織', 'GYM', NULL),
    (2, 'デフォルト店舗', 'STORE', 1);

SET sql_mode = @old_sql_mode;
ALTER TABLE organizations AUTO_INCREMENT = 3;
