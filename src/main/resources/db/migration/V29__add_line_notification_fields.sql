-- LINE通知対応（ita4-1）
-- ユーザーごとの通知方法（メール／LINE）設定と、LINE公式アカウントの友だち追加状況を保持する。
-- 友だち追加状況はMessaging APIのWebhook（follow/unfollowイベント）で更新する想定。

ALTER TABLE users
  ADD COLUMN notification_method VARCHAR(10) NOT NULL DEFAULT 'EMAIL' COMMENT '通知方法: EMAIL/LINE/BOTH',
  ADD COLUMN line_friend_added BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'LINE公式アカウントを友だち追加済みか';

-- 既にLINEログインでline_Idを持つ既存ユーザーは通知方法をLINEに寄せる（新規登録時のデフォルトはアプリ側で分岐）。
-- friend_added済みかは実際にはWebhook経由でしか判定できないため、既存ユーザーは未追加(FALSE)のまま初期化し、
-- 友だち追加イベントを受信するまではメールにフォールバックさせる（WeeklySummaryTask/MonthlySummaryTaskの分岐ロジックを参照）。
UPDATE users SET notification_method = 'LINE' WHERE line_Id IS NOT NULL;
