package com.example.traning.periodization;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 期分けプログラムのサービス層が返す読み取り用ビュー（機能見直し-1-#3）。 Web用・モバイル用のレスポンスDTOはそれぞれのControllerでこのビューから変換する（既存方針:
 * Web/モバイルで別DTO）。
 */
public final class PeriodizationViews {

  private PeriodizationViews() {}

  /** organizationIdが0なら全組織共通、それ以外はユーザーの店舗のプリセット。 */
  public record PresetSummary(
      Long id,
      String name,
      String purposeCategory,
      int totalWeeks,
      String description,
      Long organizationId) {}

  /** 目的カテゴリの表示名（Web・モバイル共通）。 */
  public static String categoryLabel(String purposeCategory) {
    if (purposeCategory == null) return "";
    return switch (purposeCategory) {
      case "BULK" -> "増量 (BULK)";
      case "CUT" -> "減量 (CUT)";
      case "MAINTENANCE" -> "維持 (MAINTENANCE)";
      case "STRENGTH" -> "筋力 (STRENGTH)";
      default -> purposeCategory;
    };
  }

  public record ItemView(
      String itemName, int displayOrder, int targetSets, Double targetWeightKg) {}

  public record DayView(
      Long dayTemplateId, String dayOfWeek, String partCode, List<ItemView> items) {}

  public record WeekView(
      int weekNumber, BigDecimal targetIntensityPct, boolean deload, List<DayView> days) {}

  /** 採用中サイクルの全体像（週送りタイムライン表示用）。 */
  public record CycleDetail(
      Long cycleId,
      String name,
      String tier,
      String status,
      LocalDate startDate,
      int totalWeeks,
      int currentWeekNumber,
      List<WeekView> weeks) {}

  /**
   * 当日の割当（設計書3-1節 getTodayAssignment）。
   *
   * @param hasActiveCycle 採用中サイクルがあるか
   * @param cycleCompleted サイクル終了後で、次の選択（renewCycle）を促す状態か（Q3-7）
   * @param dayTemplate 当日の曜日割当。休養日・サイクル無し・終了時はnull
   * @param stagnationWarning NONE/MILD/STRONG（Q3-4）
   * @param stagnantItems 停滞と判定された種目と種目ごとの判定（stagnationWarningの根拠。1種目のみの場合、全体はMILDまで）
   */
  public record TodayAssignment(
      boolean hasActiveCycle,
      boolean cycleCompleted,
      Long cycleId,
      String cycleName,
      Integer weekNumber,
      Integer totalWeeks,
      BigDecimal targetIntensityPct,
      Boolean deload,
      DayView dayTemplate,
      String stagnationWarning,
      List<ItemStagnation> stagnantItems) {}

  /** 種目ごとの停滞判定（MILD/STRONG）。 */
  public record ItemStagnation(String itemName, String level) {}

  /** 白紙作成の週別入力（createCustomCycle）。 */
  public record WeekInput(Integer weekNumber, BigDecimal targetIntensityPct, Boolean deload) {}

  /** 白紙作成の曜日別入力（createCustomCycle）。partCodeが空なら休養日。 */
  public record DayInput(
      Integer weekNumber, String dayOfWeek, String partCode, List<ItemInput> items) {}

  /** 白紙作成の入力（QA Q3-6見直しで新設、tier=INTERMEDIATE_CUSTOM）。 */
  public record CustomCycleInput(
      String name, Integer totalWeeks, List<WeekInput> weeks, List<DayInput> days) {}

  /** 白紙作成の結果。warningsは保存を妨げない補足・注意（CustomCycleRules）。 */
  public record CustomCycleResult(Long cycleId, List<CustomCycleRules.Warning> warnings) {}

  /** 白紙作成画面が参照するルール（週数範囲・注意表示の閾値と文言）。クライアント側の表示判定に使う。 */
  public record CustomCycleRulesView(
      int minTotalWeeks,
      int maxTotalWeeks,
      int longLoadStreakWarnWeeks,
      int noDeloadWarnMinWeeks,
      String shortCycleNote,
      String longLoadStreakWarning,
      String noDeloadWarning) {
    public static CustomCycleRulesView current() {
      return new CustomCycleRulesView(
          CustomCycleRules.MIN_TOTAL_WEEKS,
          CustomCycleRules.MAX_TOTAL_WEEKS,
          CustomCycleRules.LONG_LOAD_STREAK_WARN_WEEKS,
          CustomCycleRules.NO_DELOAD_WARN_MIN_WEEKS,
          CustomCycleRules.SHORT_CYCLE_NOTE,
          CustomCycleRules.LONG_LOAD_STREAK_WARNING,
          CustomCycleRules.NO_DELOAD_WARNING);
    }
  }

  /**
   * トレーナーからの案（トレーニー側の案カード・トレーナー側の送信済み一覧の表示用）。
   *
   * @param scheduledStartDate 予約中の案が始まる日（今のサイクルの終了日の翌日）。予約中以外はnull
   * @param scheduledAfterCycleName 予約中の案の前に実施している今のサイクル名。予約中以外はnull
   */
  public record ProposalView(
      Long id,
      String name,
      int totalWeeks,
      Long traineeUserId,
      String traineeName,
      Long trainerUserId,
      String trainerName,
      String status,
      String statusLabel,
      LocalDateTime sentAt,
      LocalDateTime respondedAt,
      LocalDateTime contentUpdatedAt,
      LocalDateTime startedAt,
      LocalDate scheduledStartDate,
      String scheduledAfterCycleName) {}

  /** トレーニー宛ての返事待ち（最新1件のはず）と予約中の案。 */
  public record TraineeProposals(List<ProposalView> pending, List<ProposalView> scheduled) {}

  /** 曜日別種目編集の入力1行（customizeDayTemplateItems）。 */
  public record ItemInput(String itemName, Integer targetSets) {}
}
