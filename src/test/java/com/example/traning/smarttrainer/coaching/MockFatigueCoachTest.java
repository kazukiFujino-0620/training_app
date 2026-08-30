package com.example.traning.smarttrainer.coaching;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockFatigueCoachTest {

  private final MockFatigueCoach coach = new MockFatigueCoach();

  private FatigueCalculator.FatigueResult result(Map<String, Integer> fatiguePct) {
    return new FatigueCalculator.FatigueResult(
        fatiguePct, new LinkedHashMap<>(), new LinkedHashMap<>());
  }

  @Test
  void generateComment_高疲労部位があればその部位名と数値を含むコメントを返す() {
    Map<String, Integer> pct = new LinkedHashMap<>();
    pct.put("CHEST", 30);
    pct.put("SHOULDER", 72);
    pct.put("LEG", 45);

    String comment = coach.generateComment(result(pct));

    assertThat(comment).contains("肩").contains("72");
  }

  @Test
  void generateComment_全部位が低疲労なら励ましコメントを返す() {
    Map<String, Integer> pct = new LinkedHashMap<>();
    pct.put("CHEST", 10);
    pct.put("BACK", 20);

    String comment = coach.generateComment(result(pct));

    assertThat(comment).contains("低め");
  }

  @Test
  void source_mockを返す() {
    assertThat(coach.source()).isEqualTo("mock");
  }
}
