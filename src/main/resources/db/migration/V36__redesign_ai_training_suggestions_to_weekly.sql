-- ita5-1 機能1: AIトレーニング提案の生成頻度を「トレーニングした日ごと」から
-- 「週頭（月曜）に1回、7日分をまとめて生成」に変更する（認識相違の是正）。
-- 「反映」操作自体は従来どおり1日分ずつ行う（アプリ側で該当日のみ抽出する）。
-- 本機能はこれまで実際には正常動作していなかった（createdAtのNOT NULL違反等）ため、
-- 既存データを保持する必要はなく、列の置き換え・削除のみを行う。

ALTER TABLE ai_training_suggestions
    DROP INDEX uq_ai_training_suggestions_user_date,
    CHANGE COLUMN target_date week_start_date DATE NOT NULL COMMENT '対象週の月曜日（ISO週）',
    DROP COLUMN part_code,
    DROP COLUMN comment,
    MODIFY COLUMN items_json TEXT NOT NULL COMMENT '7日分のAiSuggestedDayリストのJSON配列',
    ADD UNIQUE KEY uq_ai_training_suggestions_user_week (user_id, week_start_date);
