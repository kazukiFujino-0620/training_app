package com.example.traning.smarttrainer.coaching;

import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ita5-1 機能3のmock実装。GCPテスト環境では実際の外部AI（GPT-5 nano）を呼び出さず、
 * 最も疲労度が高い部位を元にした簡易的な解釈コメントを返す。本番環境構築時に実API連携へ差し替える。
 */
@Component
public class MockFatigueCoach implements FatigueCoach {

  private static final String SOURCE = "mock";
  private static final int HIGH_FATIGUE_THRESHOLD = 60;
  private static final Map<String, String> PART_LABEL_MAP =
      Map.of("CHEST", "胸", "BACK", "背中", "SHOULDER", "肩", "ARM", "腕", "LEG", "脚");

  @Override
  public String generateComment(FatigueCalculator.FatigueResult fatigueResult) {
    Map<String, Integer> fatiguePct = fatigueResult.fatiguePct();

    String mostFatiguedPart = null;
    int maxPct = -1;
    for (Map.Entry<String, Integer> entry : fatiguePct.entrySet()) {
      if (entry.getValue() != null && entry.getValue() > maxPct) {
        maxPct = entry.getValue();
        mostFatiguedPart = entry.getKey();
      }
    }

    if (mostFatiguedPart == null || maxPct < HIGH_FATIGUE_THRESHOLD) {
      return "（モック）全体的に疲労度は低めです。順調にトレーニングできていますね。";
    }

    String label = PART_LABEL_MAP.getOrDefault(mostFatiguedPart, mostFatiguedPart);
    return "（モック）" + label + "の疲労度が" + maxPct + "%と高めです。無理せず、回復を優先しましょう。";
  }

  @Override
  public String source() {
    return SOURCE;
  }
}
