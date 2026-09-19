ALTER TABLE training_item_master
  ADD COLUMN is_compound TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '1=複合種目(多関節), 0=単関節種目。休憩タイマー自動算出の区分に使用（機能見直し-1-#1）';

-- 初期分類（要件定義の回答: 「マシン種目は単関節、それ以外は複合」をキーワードヒューリスティックで暫定反映）。
-- 実データの最終確認は運用側でのレビューが必要（設計書「5. 確定事項の記録」参照）。
UPDATE training_item_master
SET is_compound = 0
WHERE item_name LIKE '%マシン%'
   OR item_name LIKE '%マシーン%'
   OR item_name LIKE '%カール%'
   OR item_name LIKE '%エクステンション%'
   OR item_name LIKE '%レイズ%'
   OR item_name LIKE '%フライ%'
   OR item_name LIKE '%クランチ%'
   OR item_name LIKE '%カーフ%';
