-- ita5-1 機能3: 筋肉疲労度マップのAI分析
-- その日のトレーニングが完了したタイミングで1日1回、部位別疲労度%への解釈コメントを生成し、
-- 当日キャッシュとして保存する（機能1と同じくオンデマンド生成、案B踏襲）。
-- 現時点ではmock実装（実際の外部AI呼び出しは行わない）。

CREATE TABLE ai_fatigue_comments (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    target_date  DATE         NOT NULL,
    comment      VARCHAR(500) NOT NULL,
    source       VARCHAR(30)  NOT NULL DEFAULT 'mock' COMMENT 'mock / gpt-5-nano 等、生成元の識別子',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ai_fatigue_comments_user_date (user_id, target_date),
    INDEX idx_ai_fatigue_comments_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
