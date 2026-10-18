-- 機能見直し-1-#5: 睡眠時間・安静時心拍の参考表示
-- 詳細設計: training-app/詳細設計/機能見直し-1-#5 - 睡眠時間・安静時心拍の参考表示_設計書 1章（QA Q5-1, Q5-2）

-- 1. health_sleep を体重記録と同じ「1ユーザー1日1行（手動入力・自動同期のうち最後に書き込んだ値）」に統一する。
--    これまでは取得元(source)ごとに別行を持てたため、同じ日にHEALTHKITとHEALTH_CONNECTの2行が
--    存在し得る（同じユーザーがiPhone・Androidの両方から同期した場合のみ）。
--    UNIQUE制約を変える前に重複を整理する。方針: 後から登録された行（idが大きい方）を残し、
--    それ以外は退避テーブルにコピーしてから削除する（後から確認・復元できるようにする）。

CREATE TABLE health_sleep_v43_removed (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  sleep_date DATE NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  duration_minutes INT NOT NULL,
  source VARCHAR(20) NOT NULL,
  synced_at DATETIME NOT NULL,
  removed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO health_sleep_v43_removed
  (id, user_id, sleep_date, start_time, end_time, duration_minutes, source, synced_at)
SELECT h1.id, h1.user_id, h1.sleep_date, h1.start_time, h1.end_time, h1.duration_minutes,
       h1.source, h1.synced_at
FROM health_sleep h1
WHERE EXISTS (
  SELECT 1 FROM (SELECT id, user_id, sleep_date FROM health_sleep) h2
  WHERE h2.user_id = h1.user_id AND h2.sleep_date = h1.sleep_date AND h2.id > h1.id
);

DELETE h1 FROM health_sleep h1
INNER JOIN health_sleep_v43_removed r ON r.id = h1.id;

ALTER TABLE health_sleep
  DROP INDEX uq_health_sleep_user_date_source,
  ADD UNIQUE KEY uq_health_sleep_user_date (user_id, sleep_date),
  MODIFY COLUMN source VARCHAR(20) NOT NULL COMMENT 'MANUAL/HEALTHKIT/HEALTH_CONNECT',
  -- 手動入力は睡眠時間（時間・分）のみ受け付けるため、寝た時刻・起きた時刻は手動入力の行では空になる（QA Q5確認2件目でA確定）
  MODIFY COLUMN start_time DATETIME NULL,
  MODIFY COLUMN end_time DATETIME NULL;

-- 2. 安静時心拍: health_heart_rate（日中の平均/最小/最大、自動同期専用）とは別指標のため新規の専用テーブル（QA Q5-2）。
CREATE TABLE health_resting_heart_rate (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  record_date DATE NOT NULL,
  resting_bpm INT NOT NULL,
  source VARCHAR(20) NOT NULL COMMENT 'MANUAL/HEALTHKIT/HEALTH_CONNECT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_health_resting_hr_user_date (user_id, record_date),
  KEY idx_health_resting_hr_user_id (user_id)
);
