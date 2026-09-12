-- 有酸素運動（カーディオ）対応（ita2-1）
-- 部位マスタに「カーディオ」区分を新設し、代表的な有酸素運動種目を登録する。
-- training_details には有酸素運動固有の記録項目（時間・距離・平均心拍数・消費カロリー）を追加する。
-- いずれも筋トレのセット（weight/reps）とは独立した任意項目のため NULL 許容とする。

INSERT INTO training_part_master (part_code, part_name, display_order, organization_id)
VALUES ('CARDIO', 'カーディオ', 6, 0);

INSERT INTO training_item_master (part_code, item_name, display_order, master_flg, organization_id)
VALUES
  ('CARDIO', 'ランニングマシン', 1, 1, 0),
  ('CARDIO', 'エアロバイク', 2, 1, 0),
  ('CARDIO', 'ローイングマシン', 3, 1, 0),
  ('CARDIO', 'クロストレーナー', 4, 1, 0),
  ('CARDIO', '水泳', 5, 1, 0);

-- distance_km / calories_kcal はDOUBLE（既存のweight列と同じ方式）。
-- TrainingDetailはDoma2とJakarta Persistence(JPA)アノテーションを併用しており、
-- JPA側のスキーマ検証（Hibernate ddl-auto=validate）がJavaのDouble型に対してDOUBLE列を期待するため、
-- DECIMALにすると起動時にSchema-validationエラーになる（既存のweight/count等と同じ理由でDOUBLEに統一）。
ALTER TABLE training_details
  ADD COLUMN duration_min INT NULL COMMENT '有酸素運動の実施時間（分）。開始〜完了のタイマーから自動反映',
  ADD COLUMN distance_km DOUBLE NULL COMMENT '有酸素運動の走行・移動距離（km）。マシンの表示値を手入力',
  ADD COLUMN avg_heart_rate_bpm INT NULL COMMENT '有酸素運動の平均心拍数（bpm）。マシンの表示値を手入力',
  ADD COLUMN calories_kcal DOUBLE NULL COMMENT '有酸素運動の消費カロリー（kcal）。マシンの表示値を手入力（筋トレの消費カロリー計算式(ita2-2)とは別管理）';
