package com.example.traning.mobile.dto;

import com.example.traning.goal.FrequencyGoalSummary;
import java.util.List;

/**
 * モバイル: カレンダータブの「週の目標」カードと設定シートの表示データ（GET /api/mobile/frequency-goal、機能見直し-1-#7）。
 *
 * <p>表示文言はWeb版と揃えるためサーバーで組み立てて返す。数値（achievementPct等）は将来の表示変更用に併せて返す。
 */
public record MobileFrequencyGoalResponse(
    boolean goalSet,
    String goalLabel,
    String thisWeekLabel,
    String resultLabel,
    String percentLabel,
    List<String> notes,
    boolean fromProgram,
    Integer thisWeekTarget,
    int thisWeekDoneDays,
    int evaluatedWeeks,
    int achievedWeeks,
    Integer achievementPct,
    Integer manualTarget,
    boolean programActive,
    String programSheetLabel) {

  public static MobileFrequencyGoalResponse from(FrequencyGoalSummary s) {
    return new MobileFrequencyGoalResponse(
        s.goalSet(),
        s.goalLabel(),
        s.thisWeekLabel(),
        s.resultLabel(),
        s.percentLabel(),
        s.notes(),
        s.thisWeekFromProgram(),
        s.thisWeekTarget(),
        s.thisWeekDoneDays(),
        s.evaluatedWeeks(),
        s.achievedWeeks(),
        s.achievementPct(),
        s.manualTarget(),
        s.programActive(),
        s.programSheetLabel());
  }
}
