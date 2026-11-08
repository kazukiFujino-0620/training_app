package com.example.traning.smarttrainer.autogen;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** メニュー自動作成（機能見直し-1-#4）のサービスの入出力。モバイルAPIのレスポンスとしてもそのまま使う。 */
public final class WorkoutGenerationViews {

  private WorkoutGenerationViews() {}

  /** 生成モード（QA Q4-5: 同一APIで自動判別）。 */
  public enum Mode {
    /** 部位指定もプログラムの部位も無い、または部位を指定した（プログラム無し）。 */
    ADHOC,
    /** 部位指定が無く、プログラムの当日の部位・強度%で組んだ。 */
    CYCLE_COMPLEMENT,
    /** 部位を指定し、強度%のみプログラムに合わせた（QA Q4-6）。 */
    USER_OVERRIDE
  }

  /**
   * @param date 対象日（null=今日）
   * @param desiredPartCodes 指定順が優先度
   * @param availableEquipmentIds null=器具で絞り込まない
   * @param excludeItemNames その場限りの除外（保存済みの除外と合わせて使う）
   */
  public record GenerateCommand(
      Integer availableMinutes,
      LocalDate date,
      List<String> desiredPartCodes,
      List<Long> availableEquipmentIds,
      List<String> excludeItemNames) {}

  public record PartOption(String partCode, String partName) {}

  public record EquipmentView(Long id, String name, String category) {}

  public record ExclusionView(
      String itemName,
      String partCode,
      String partName,
      String reason,
      LocalDate excludedSince,
      LocalDateTime nextConfirmationDueAt,
      boolean needsReconfirmation) {}

  /** 対象日のプログラムの枠。partCode=null は休養日。 */
  public record ProgramDayView(
      String cycleName,
      int weekNumber,
      int totalWeeks,
      Double targetIntensityPct,
      String partCode,
      String partName) {}

  /** 条件入力画面の表示に必要な情報。 */
  public record FormView(
      int minAvailableMinutes,
      List<PartOption> parts,
      List<EquipmentView> equipment,
      List<ExclusionView> exclusions,
      ProgramDayView program) {}

  public record GeneratedAlternative(
      String itemName,
      String partCode,
      boolean compound,
      boolean bodyweight,
      int reps,
      Double weightKg,
      int restSeconds,
      String reason) {}

  public record GeneratedItem(
      String itemName,
      String partCode,
      String partName,
      boolean compound,
      boolean bodyweight,
      int sets,
      int reps,
      Double weightKg,
      int restSeconds,
      String reason,
      String warning,
      List<GeneratedAlternative> alternatives) {}

  /**
   * @param status OK / NO_CANDIDATES（条件に合う種目が無い）/ TOO_SHORT（最低構成が時間に入らない）
   * @param intensityPct 重量の計算に使った強度(%)
   * @param itemsNeedingReconfirmation 再確認の期限が来た除外種目（画面で結果の前に確認する）
   */
  public record GenerationResult(
      String status,
      Mode mode,
      ProgramDayView program,
      double intensityPct,
      int availableMinutes,
      int estimatedMinutes,
      List<GeneratedItem> items,
      List<ExclusionView> itemsNeedingReconfirmation) {}
}
