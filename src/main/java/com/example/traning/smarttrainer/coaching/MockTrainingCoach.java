package com.example.traning.smarttrainer.coaching;

import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.RecommendedItem;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ita5-1 機能1のmock実装。GCPテスト環境では実際の外部AI（Claude Haiku 4.5）を呼び出さず、 既存のルールベース推奨（{@link
 * DailyRecommendation}）をそのまま構造化データとして採用し、 テンプレートの一言コメントを添えて返す。本番環境構築時に実API連携の実装へ差し替える。
 */
@Component
public class MockTrainingCoach implements TrainingCoach {

  private static final String SOURCE = "mock";

  @Override
  public CoachingResult generate(DailyRecommendation recommendation) {
    if (recommendation.restDayRecommended()) {
      return new CoachingResult(
          "（モック）全部位がまだ疲労中のようです。今日は軽めの有酸素や休養に充ててみましょう。", List.of());
    }

    List<AiSuggestedItem> items =
        recommendation.items().stream().map(MockTrainingCoach::toSuggestedItem).toList();

    String comment =
        "（モック）"
            + recommendation.partLabel()
            + "の種目を中心に組んでみました。"
            + recommendation.reasonLabel();

    return new CoachingResult(comment, items);
  }

  @Override
  public String source() {
    return SOURCE;
  }

  private static AiSuggestedItem toSuggestedItem(RecommendedItem item) {
    return new AiSuggestedItem(
        item.itemName(),
        item.weightMin(),
        item.weightMax(),
        item.repsMin(),
        item.repsMax(),
        item.sets());
  }
}
