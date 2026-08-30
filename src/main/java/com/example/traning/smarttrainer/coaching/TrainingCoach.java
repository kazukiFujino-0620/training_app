package com.example.traning.smarttrainer.coaching;

import com.example.traning.smarttrainer.recommendation.DailyRecommendation;

/**
 * AIトレーニング提案（ita5-1 機能1）の生成インターフェース。 実装は{@link MockTrainingCoach}（現在有効）。
 * 本番AI連携時は同インターフェースのClaude実装に差し替える想定（呼び出し元・キャッシュ・安全性チェックのロジックは変更不要）。
 */
public interface TrainingCoach {

  /** ルールベース推奨（{@link DailyRecommendation}）を元に、構造化データ＋一言コメントを生成する。 */
  CoachingResult generate(DailyRecommendation recommendation);

  /** このコーチの生成元識別子（DB保存用。例: "mock", "claude-haiku-4-5"）。 */
  String source();
}
