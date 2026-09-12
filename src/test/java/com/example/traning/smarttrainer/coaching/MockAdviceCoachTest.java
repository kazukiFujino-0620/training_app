package com.example.traning.smarttrainer.coaching;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.training.Training;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockAdviceCoachTest {

  private final MockAdviceCoach coach = new MockAdviceCoach();

  private FatigueCalculator.FatigueResult fatigueResult(Map<String, Integer> pct) {
    return new FatigueCalculator.FatigueResult(pct, new LinkedHashMap<>(), new LinkedHashMap<>());
  }

  private Training training(boolean completed) {
    Training t = new Training();
    t.setIsAllCompleted(completed);
    return t;
  }

  private AdviceContext context(
      List<Training> trainings,
      Map<String, Integer> fatiguePct,
      DailyRecommendation recommendation,
      List<PersonalRecord> personalRecords) {
    return new AdviceContext(
        "山田太郎",
        LocalDate.of(2026, 8, 31),
        trainings,
        recommendation,
        fatigueResult(fatiguePct),
        personalRecords);
  }

  @Test
  void generateDraft_トレーニー名と対象日を含む() {
    String draft =
        coach.generateDraft(context(List.of(), Map.of(), DailyRecommendation.restDay(), List.of()));

    assertThat(draft).contains("山田太郎").contains("8/31");
  }

  @Test
  void generateDraft_記録が無い日は記録なしの文言になる() {
    String draft =
        coach.generateDraft(context(List.of(), Map.of(), DailyRecommendation.restDay(), List.of()));

    assertThat(draft).contains("記録がないようです");
  }

  @Test
  void generateDraft_全種目完了なら達成を称える文言になる() {
    String draft =
        coach.generateDraft(
            context(
                List.of(training(true), training(true)),
                Map.of(),
                DailyRecommendation.restDay(),
                List.of()));

    assertThat(draft).contains("2種目").contains("やり切れていて素晴らしい");
  }

  @Test
  void generateDraft_未完了があれば無理しない文言になる() {
    String draft =
        coach.generateDraft(
            context(
                List.of(training(true), training(false)),
                Map.of(),
                DailyRecommendation.restDay(),
                List.of()));

    assertThat(draft).contains("自分のペースで");
  }

  @Test
  void generateDraft_高疲労部位があれば回復を促す文言を含む() {
    Map<String, Integer> pct = new LinkedHashMap<>();
    pct.put("SHOULDER", 72);

    String draft =
        coach.generateDraft(context(List.of(), pct, DailyRecommendation.restDay(), List.of()));

    assertThat(draft).contains("肩").contains("回復");
  }

  @Test
  void generateDraft_休養日でなければ推奨部位への言及を含む() {
    DailyRecommendation recommendation =
        new DailyRecommendation("LEG", "脚", "しばらく実施していません", List.of(), false);

    String draft = coach.generateDraft(context(List.of(), Map.of(), recommendation, List.of()));

    assertThat(draft).contains("脚を中心に");
  }

  @Test
  void generateDraft_PRがあれば自己ベスト更新の文言を含む() {
    PersonalRecord pr = new PersonalRecord();
    pr.setItemName("ベンチプレス");

    String draft =
        coach.generateDraft(
            context(List.of(), Map.of(), DailyRecommendation.restDay(), List.of(pr)));

    assertThat(draft).contains("自己ベスト更新");
  }

  @Test
  void source_mockを返す() {
    assertThat(coach.source()).isEqualTo("mock");
  }
}
