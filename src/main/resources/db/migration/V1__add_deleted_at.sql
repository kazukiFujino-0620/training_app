-- 論理削除カラム追加
-- NULL = アクティブ, NOT NULL = 論理削除済み (値 = 削除日時)
-- 保護期間 7 年経過後、バッチが物理削除する

ALTER TABLE trainings        ADD COLUMN deleted_at DATETIME NULL DEFAULT NULL;
ALTER TABLE training_details ADD COLUMN deleted_at DATETIME NULL DEFAULT NULL;
ALTER TABLE users            ADD COLUMN deleted_at DATETIME NULL DEFAULT NULL;
