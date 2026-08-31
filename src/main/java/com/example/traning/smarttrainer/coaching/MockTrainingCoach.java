package com.example.traning.smarttrainer.coaching;

import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.pr.PersonalRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ita5-1 機能1のmock実装。GCPテスト環境では実際の外部AI（Claude Haiku 4.5）を呼び出さず、
 * 部位の固定ローテーション（月:胸/火:背中/水:休養/木:肩/金:腕/土:脚/日:休養）を元にした 週間プランを返す。本番環境構築時に実API連携の実装へ差し替える。
 */
@Component
public class MockTrainingCoach implements TrainingCoach {

  private static final String SOURCE = "mock";

  /** 月曜(0)〜日曜(6)の部位ローテーション。nullは休養日。 */
  private static final String[] WEEKLY_ROTATION = {
    "CHEST", "BACK", null, "SHOULDER", "ARM", "LEG", null
  };

  private static final Map<String, String> PART_LABEL_MAP =
      Map.of("CHEST", "胸", "BACK", "背中", "SHOULDER", "肩", "ARM", "腕", "LEG", "脚");

  /** PRが無い種目向けの部位ごとのデフォルト重量レンジ（kg）。 */
  private static final Map<String, double[]> DEFAULT_WEIGHT_RANGE_BY_PART =
      Map.of(
          "CHEST", new double[] {20.0, 30.0},
          "BACK", new double[] {30.0, 40.0},
          "SHOULDER", new double[] {10.0, 15.0},
          "ARM", new double[] {8.0, 12.0},
          "LEG", new double[] {30.0, 40.0});

  private static final int ITEMS_PER_DAY = 3;
  private static final int REPS_MIN = 8;
  private static final int REPS_MAX = 10;
  private static final int SETS = 3;
  private static final double PR_RATIO_MIN = 0.7;
  private static final double PR_RATIO_MAX = 0.8;

  @Override
  public List<AiSuggestedDay> generateWeeklyPlan(
      LocalDate weekStartDate,
      Map<String, List<TrainingItemMaster>> masterItemsByPart,
      Map<String, PersonalRecord> personalRecordsByItemName) {
    List<AiSuggestedDay> days = new ArrayList<>();
    for (int i = 0; i < WEEKLY_ROTATION.length; i++) {
      LocalDate date = weekStartDate.plusDays(i);
      String partCode = WEEKLY_ROTATION[i];
      if (partCode == null) {
        days.add(
            new AiSuggestedDay(date, null, null, "（モック）今日は休養日です。ゆっくり回復に努めましょう。", List.of(), true));
        continue;
      }

      String label = PART_LABEL_MAP.get(partCode);
      List<AiSuggestedItem> items =
          buildItems(
              masterItemsByPart.getOrDefault(partCode, List.of()),
              partCode,
              personalRecordsByItemName);
      String comment = "（モック）" + label + "の種目を中心に組んでみました。";
      days.add(new AiSuggestedDay(date, partCode, label, comment, items, false));
    }
    return days;
  }

  private List<AiSuggestedItem> buildItems(
      List<TrainingItemMaster> masters,
      String partCode,
      Map<String, PersonalRecord> personalRecordsByItemName) {
    double[] fallbackRange =
        DEFAULT_WEIGHT_RANGE_BY_PART.getOrDefault(partCode, new double[] {10.0, 20.0});

    return masters.stream()
        .limit(ITEMS_PER_DAY)
        .map(
            master -> {
              PersonalRecord pr = personalRecordsByItemName.get(master.getItemName());
              double weightMin;
              double weightMax;
              if (pr != null && pr.getMaxWeight() != null) {
                weightMin = Math.round(pr.getMaxWeight() * PR_RATIO_MIN * 2) / 2.0;
                weightMax = Math.round(pr.getMaxWeight() * PR_RATIO_MAX * 2) / 2.0;
              } else {
                weightMin = fallbackRange[0];
                weightMax = fallbackRange[1];
              }
              return new AiSuggestedItem(
                  master.getItemName(), weightMin, weightMax, REPS_MIN, REPS_MAX, SETS);
            })
        .toList();
  }

  @Override
  public String source() {
    return SOURCE;
  }
}
