package com.example.traning.smarttrainer.coaching;

import com.example.traning.smarttrainer.recommendation.FatigueCalculator;

/**
 * 筋肉疲労度マップのAI分析（ita5-1 機能3）の生成インターフェース。 実装は{@link MockFatigueCoach}（現在有効）。 本番AI連携時は同インターフェースのGPT-5
 * nano実装に差し替える想定。
 */
public interface FatigueCoach {

  /** 部位別疲労度%（{@link FatigueCalculator.FatigueResult}）を元に、短い解釈コメントを生成する。 */
  String generateComment(FatigueCalculator.FatigueResult fatigueResult);

  /** このコーチの生成元識別子（DB保存用。例: "mock", "gpt-5-nano"）。 */
  String source();
}
