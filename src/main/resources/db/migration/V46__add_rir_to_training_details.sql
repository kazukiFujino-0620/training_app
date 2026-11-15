-- 機能見直し-1-#18: RIR（余力。そのセットの後あと何回できたか）の記録
-- 本番セット（set_type=MAIN）の完了時にモバイルで任意入力する。未入力（スキップ）はNULL。
-- 0〜5の整数で、5は「5回以上」を表す。#4のメニュー自動作成や疲労表示への反映には使わない（記録と表示のみ）。
-- TrainingDetailはJPAのスキーマ検証（ddl-auto=validate）がJavaのInteger型に対してINT列を期待するため、
-- TINYINTではなく既存のduration_min等と同じINTにする。
ALTER TABLE training_details
  ADD COLUMN rir INT NULL COMMENT 'RIR（余力）。0〜5、5は「5回以上」。本番セットのみ・任意入力、未入力はNULL';
