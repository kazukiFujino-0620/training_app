-- ita5-1 機能1: AIトレーニング提案（Web/mobile共通）
-- トレーニングした日ごとに1回、構造化データ（種目・重量レンジ・回数レンジ・セット数）と
-- 一言コメントをまとめて生成し、当日キャッシュとして保存する（オンデマンド生成、案B）。
-- 現時点ではmock実装（実際の外部AI呼び出しは行わない）。sourceカラムで生成元を区別する。

CREATE TABLE ai_training_suggestions (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    target_date  DATE         NOT NULL,
    part_code    VARCHAR(50)  NULL COMMENT '休養日推奨の場合はNULL',
    comment      VARCHAR(1000) NOT NULL,
    items_json   TEXT         NOT NULL COMMENT '種目・重量レンジ・回数レンジ・セット数のJSON配列。休養日推奨時は空配列',
    source       VARCHAR(30)  NOT NULL DEFAULT 'mock' COMMENT 'mock / claude-haiku-4-5 等、生成元の識別子',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ai_training_suggestions_user_date (user_id, target_date),
    INDEX idx_ai_training_suggestions_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ita5-1 全機能共通: 外部AIへのデータ送信に対する初回同意フラグ
ALTER TABLE users ADD COLUMN ai_advice_consent BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'AI機能（トレーニング提案・疲労度分析・トレーナーアドバイス下書き）利用への同意';
