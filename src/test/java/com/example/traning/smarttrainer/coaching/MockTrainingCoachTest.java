package com.example.traning.smarttrainer.coaching;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.pr.PersonalRecord;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockTrainingCoachTest {

  private final MockTrainingCoach coach = new MockTrainingCoach();
  private final LocalDate weekStart = LocalDate.of(2026, 8, 31); // 月曜

  private TrainingItemMaster item(String name) {
    TrainingItemMaster master = new TrainingItemMaster();
    master.setItemName(name);
    return master;
  }

  @Test
  void generateWeeklyPlan_月曜始まりの7日分を返す() {
    List<AiSuggestedDay> days = coach.generateWeeklyPlan(weekStart, Map.of(), Map.of());

    assertThat(days).hasSize(7);
    for (int i = 0; i < 7; i++) {
      assertThat(days.get(i).date()).isEqualTo(weekStart.plusDays(i));
    }
  }

  @Test
  void generateWeeklyPlan_休養日は種目が空でrestDayRecommendedがtrue() {
    List<AiSuggestedDay> days = coach.generateWeeklyPlan(weekStart, Map.of(), Map.of());

    AiSuggestedDay wednesday = days.get(2); // 水曜
    assertThat(wednesday.date().getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    assertThat(wednesday.restDayRecommended()).isTrue();
    assertThat(wednesday.partCode()).isNull();
    assertThat(wednesday.items()).isEmpty();
  }

  @Test
  void generateWeeklyPlan_トレーニング日は部位マスタから種目を最大3件まで取得する() {
    Map<String, List<TrainingItemMaster>> masterItemsByPart =
        Map.of(
            "CHEST", List.of(item("ベンチプレス"), item("ダンベルフライ"), item("インクラインベンチ"), item("ケーブルクロス")));

    List<AiSuggestedDay> days = coach.generateWeeklyPlan(weekStart, masterItemsByPart, Map.of());

    AiSuggestedDay monday = days.get(0);
    assertThat(monday.restDayRecommended()).isFalse();
    assertThat(monday.partCode()).isEqualTo("CHEST");
    assertThat(monday.partLabel()).isEqualTo("胸");
    assertThat(monday.items()).hasSize(3);
    assertThat(monday.comment()).contains("胸");
  }

  @Test
  void generateWeeklyPlan_PRがある種目は重量レンジをPRの70から80パーセントにする() {
    Map<String, List<TrainingItemMaster>> masterItemsByPart =
        Map.of("CHEST", List.of(item("ベンチプレス")));
    PersonalRecord pr = new PersonalRecord();
    pr.setItemName("ベンチプレス");
    pr.setMaxWeight(100.0);

    List<AiSuggestedDay> days =
        coach.generateWeeklyPlan(weekStart, masterItemsByPart, Map.of("ベンチプレス", pr));

    AiSuggestedItem suggested = days.get(0).items().get(0);
    assertThat(suggested.weightMin()).isEqualTo(70.0);
    assertThat(suggested.weightMax()).isEqualTo(80.0);
  }

  @Test
  void generateWeeklyPlan_PRが無い種目は部位ごとのデフォルトレンジを使う() {
    Map<String, List<TrainingItemMaster>> masterItemsByPart =
        Map.of("ARM", List.of(item("アームカール")));

    List<AiSuggestedDay> days = coach.generateWeeklyPlan(weekStart, masterItemsByPart, Map.of());

    AiSuggestedDay friday = days.get(4); // 金曜=腕
    assertThat(friday.partCode()).isEqualTo("ARM");
    AiSuggestedItem suggested = friday.items().get(0);
    assertThat(suggested.weightMin()).isEqualTo(8.0);
    assertThat(suggested.weightMax()).isEqualTo(12.0);
  }

  @Test
  void source_mockを返す() {
    assertThat(coach.source()).isEqualTo("mock");
  }
}
