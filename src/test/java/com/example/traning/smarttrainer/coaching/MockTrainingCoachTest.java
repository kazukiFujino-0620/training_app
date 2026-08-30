package com.example.traning.smarttrainer.coaching;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.RecommendedItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockTrainingCoachTest {

  private final MockTrainingCoach coach = new MockTrainingCoach();

  @Test
  void generate_休養日推奨の場合はitemsが空でコメントのみ返す() {
    DailyRecommendation restDay = DailyRecommendation.restDay();

    CoachingResult result = coach.generate(restDay);

    assertThat(result.items()).isEmpty();
    assertThat(result.comment()).isNotBlank();
  }

  @Test
  void generate_推奨種目をそのまま構造化データへ変換する() {
    RecommendedItem item = new RecommendedItem("ベンチプレス", 60.0, 80.0, 5, 10, 3);
    DailyRecommendation recommendation =
        new DailyRecommendation("CHEST", "胸", "しばらく胸を鍛えていません", List.of(item), false);

    CoachingResult result = coach.generate(recommendation);

    assertThat(result.items()).hasSize(1);
    AiSuggestedItem suggested = result.items().get(0);
    assertThat(suggested.itemName()).isEqualTo("ベンチプレス");
    assertThat(suggested.weightMin()).isEqualTo(60.0);
    assertThat(suggested.weightMax()).isEqualTo(80.0);
    assertThat(suggested.repsMin()).isEqualTo(5);
    assertThat(suggested.repsMax()).isEqualTo(10);
    assertThat(suggested.sets()).isEqualTo(3);
    assertThat(result.comment()).contains("胸").contains("しばらく胸を鍛えていません");
  }

  @Test
  void source_mockを返す() {
    assertThat(coach.source()).isEqualTo("mock");
  }
}
