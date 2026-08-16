-- ita2-2: 消費カロリー計算見直し（力学的仕事量ベース、案B）
-- 仕事量(J) = 挙上重量(kg) × 9.8 × 可動域ROM(m) × 回数
-- 消費カロリー(kcal) = 仕事量(J) ÷ 筋効率(約20%) ÷ 4184
-- 種目ごとの可動域（ROM）目安値を種目マスタに追加する。初期値は一般的な可動域の目安であり後日調整可。

ALTER TABLE training_item_master
  ADD COLUMN range_of_motion_m DECIMAL(3,2) NOT NULL DEFAULT 0.40
  COMMENT '消費カロリー計算用の可動域目安値(m)。未整備の種目にはデフォルト値0.40を適用';

-- CHEST
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'ベンチプレス';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'インクラインベンチプレス';
UPDATE training_item_master SET range_of_motion_m = 0.50 WHERE item_name = 'ダンベルベンチプレス';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ダンベルフライ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'インクラインダンベルフライ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ケーブルクロスオーバー';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ケーブルフライ';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'スミスマシンインクラインベンチプレス';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'スミスマシンベンチプレス';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ダンベルプルオーバー';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'チェストプレスマシン';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ディップス';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ディップチンアシスト';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ペックデッキフライ';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ワンハンドケーブルフライ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = '加重ディップス';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = '腕立て伏せ';

-- BACK
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'Tバーロウ';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'アシストプルアップマシン';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ケーブルEZバープレスダウン';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'シーテッドケーブルロウ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'シーテッドロウマシン';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'チンアップ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ベントオーバーロウ';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'ラットプルダウン';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ローローマシン';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ワンハンドシーテッドケーブルロウ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ワンハンドダンベルロウ';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = '加重プルアップ';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = '懸垂（順手）';

-- ARM
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'EZバーカール';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'EZバーライイングトライセプスエクステンション';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'インクラインダンベルカール';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ケーブルカール';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'ケーブルプレスダウン';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'ケーブルローププッシュダウン';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ダンベルカール';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'ダンベルプリーチャーカール';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'ダンベルフレンチプレス';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'ナローベンチプレス';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'バーベルカール';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ハンマーカール';

-- SHOULDER
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'EZバーアップライトロウ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'アーノルドプレス';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'インクラインダンベルショルダープレス';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'オーバーヘッドプレス';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'シーテッドダンベルサイドレイズ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'シーテッドダンベルプレス';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ショルダーブレスマシン';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ショルダープレスマシン';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ダンベルサイドレイズ';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ダンベルフロントレイズ';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'フェイスプル';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'リアデルトマシン';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ワンハンドケーブルサイドレイズ';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'ワンハンドダンベルインクラインサイドレイズ';

-- LEG
UPDATE training_item_master SET range_of_motion_m = 0.10 WHERE item_name = 'カーフレイズ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'シーテッドレッグプレス';
UPDATE training_item_master SET range_of_motion_m = 0.55 WHERE item_name = 'スクワット';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'スプリットスクワット';
UPDATE training_item_master SET range_of_motion_m = 0.55 WHERE item_name = 'スミスマシンスクワット';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'ダンベルブルガリアンスクアット';
UPDATE training_item_master SET range_of_motion_m = 0.55 WHERE item_name = 'デッドリフト';
UPDATE training_item_master SET range_of_motion_m = 0.55 WHERE item_name = 'バックスクワット';
UPDATE training_item_master SET range_of_motion_m = 0.55 WHERE item_name = 'バッグスクワット';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'ヒップスラスト';
UPDATE training_item_master SET range_of_motion_m = 0.30 WHERE item_name = 'ヒップスラストマシン';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'ランジ';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'ルーマニアンデッドリフト';
UPDATE training_item_master SET range_of_motion_m = 0.40 WHERE item_name = 'レッグエクステンション';
UPDATE training_item_master SET range_of_motion_m = 0.35 WHERE item_name = 'レッグカール';
UPDATE training_item_master SET range_of_motion_m = 0.45 WHERE item_name = 'レッグプレス';
