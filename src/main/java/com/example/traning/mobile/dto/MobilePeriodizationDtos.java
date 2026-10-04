package com.example.traning.mobile.dto;

import com.example.traning.periodization.CustomCycleRules;
import com.example.traning.periodization.PeriodizationViews;
import com.example.traning.periodization.RenewChoice;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 期分けプログラムのモバイル用DTO（機能見直し-1-#3 詳細設計書5-1節）。 Web用（/api/periodization）のレスポンスとは別クラスとし、使い回さない。 */
public final class MobilePeriodizationDtos {

  private MobilePeriodizationDtos() {}

  public record PresetResponse(
      Long id,
      String name,
      String purposeCategory,
      String purposeLabel,
      int totalWeeks,
      String description) {
    public static PresetResponse from(PeriodizationViews.PresetSummary p) {
      return new PresetResponse(
          p.id(),
          p.name(),
          p.purposeCategory(),
          PeriodizationViews.categoryLabel(p.purposeCategory()),
          p.totalWeeks(),
          p.description());
    }
  }

  public record ItemResponse(String itemName, int targetSets, Double targetWeightKg) {
    static ItemResponse from(PeriodizationViews.ItemView i) {
      return new ItemResponse(i.itemName(), i.targetSets(), i.targetWeightKg());
    }
  }

  public record DayResponse(
      Long dayTemplateId, String dayOfWeek, String partCode, List<ItemResponse> items) {
    static DayResponse from(PeriodizationViews.DayView d) {
      if (d == null) return null;
      return new DayResponse(
          d.dayTemplateId(),
          d.dayOfWeek(),
          d.partCode(),
          d.items().stream().map(ItemResponse::from).toList());
    }
  }

  public record WeekResponse(
      int weekNumber, Double targetIntensityPct, boolean deload, List<DayResponse> days) {
    static WeekResponse from(PeriodizationViews.WeekView w) {
      return new WeekResponse(
          w.weekNumber(),
          w.targetIntensityPct() != null ? w.targetIntensityPct().doubleValue() : null,
          w.deload(),
          w.days().stream().map(DayResponse::from).toList());
    }
  }

  public record CycleResponse(
      Long cycleId,
      String name,
      String tier,
      String startDate,
      int totalWeeks,
      int currentWeekNumber,
      List<WeekResponse> weeks) {
    public static CycleResponse from(PeriodizationViews.CycleDetail c) {
      return new CycleResponse(
          c.cycleId(),
          c.name(),
          c.tier(),
          c.startDate().toString(),
          c.totalWeeks(),
          c.currentWeekNumber(),
          c.weeks().stream().map(WeekResponse::from).toList());
    }
  }

  public record TodayResponse(
      boolean hasActiveCycle,
      boolean cycleCompleted,
      Long cycleId,
      String cycleName,
      Integer weekNumber,
      Integer totalWeeks,
      Double targetIntensityPct,
      Boolean deload,
      DayResponse day,
      String stagnationWarning,
      List<StagnantItemResponse> stagnantItems) {
    public static TodayResponse from(PeriodizationViews.TodayAssignment t) {
      return new TodayResponse(
          t.hasActiveCycle(),
          t.cycleCompleted(),
          t.cycleId(),
          t.cycleName(),
          t.weekNumber(),
          t.totalWeeks(),
          t.targetIntensityPct() != null ? t.targetIntensityPct().doubleValue() : null,
          t.deload(),
          DayResponse.from(t.dayTemplate()),
          t.stagnationWarning(),
          t.stagnantItems().stream()
              .map(i -> new StagnantItemResponse(i.itemName(), i.level()))
              .toList());
    }
  }

  public record StagnantItemResponse(String itemName, String level) {}

  public record AdoptRequest(@NotNull Long presetProgramId) {}

  public record CustomizeItemsRequest(@NotNull List<Item> items) {
    public record Item(String itemName, Integer targetSets) {}
  }

  /** 白紙から組むリクエスト（QA Q3-6見直しで新設）。 */
  public record CustomCycleRequest(
      @NotNull String name,
      @NotNull Integer totalWeeks,
      @NotNull List<WeekInput> weeks,
      List<DayInput> days) {

    public record WeekInput(Integer weekNumber, Double targetIntensityPct, Boolean deload) {}

    public record DayInput(
        Integer weekNumber,
        String dayOfWeek,
        String partCode,
        List<CustomizeItemsRequest.Item> items) {}

    public PeriodizationViews.CustomCycleInput toInput() {
      return new PeriodizationViews.CustomCycleInput(
          name,
          totalWeeks,
          weeks.stream()
              .map(
                  w ->
                      new PeriodizationViews.WeekInput(
                          w.weekNumber(),
                          w.targetIntensityPct() == null
                              ? null
                              : java.math.BigDecimal.valueOf(w.targetIntensityPct()),
                          w.deload()))
              .toList(),
          days == null
              ? List.of()
              : days.stream()
                  .map(
                      d ->
                          new PeriodizationViews.DayInput(
                              d.weekNumber(),
                              d.dayOfWeek(),
                              d.partCode(),
                              d.items() == null
                                  ? List.of()
                                  : d.items().stream()
                                      .map(
                                          i ->
                                              new PeriodizationViews.ItemInput(
                                                  i.itemName(), i.targetSets()))
                                      .toList()))
                  .toList());
    }
  }

  public record RenewRequest(@NotNull RenewChoice choice, Long presetProgramId) {}

  /** 白紙作成の結果。warningsは保存を妨げない注意の文言。 */
  public record CustomCycleResponse(Long cycleId, List<String> warnings) {
    public static CustomCycleResponse from(PeriodizationViews.CustomCycleResult r) {
      return new CustomCycleResponse(
          r.cycleId(), r.warnings().stream().map(CustomCycleRules.Warning::message).toList());
    }
  }

  /** 白紙作成画面用のルール（週数範囲・注意表示の閾値と文言）。 */
  public record CustomCycleRulesResponse(
      int minTotalWeeks,
      int maxTotalWeeks,
      int longLoadStreakWarnWeeks,
      int noDeloadWarnMinWeeks,
      String shortCycleNote,
      String longLoadStreakWarning,
      String noDeloadWarning) {
    public static CustomCycleRulesResponse current() {
      return new CustomCycleRulesResponse(
          CustomCycleRules.MIN_TOTAL_WEEKS,
          CustomCycleRules.MAX_TOTAL_WEEKS,
          CustomCycleRules.LONG_LOAD_STREAK_WARN_WEEKS,
          CustomCycleRules.NO_DELOAD_WARN_MIN_WEEKS,
          CustomCycleRules.SHORT_CYCLE_NOTE,
          CustomCycleRules.LONG_LOAD_STREAK_WARNING,
          CustomCycleRules.NO_DELOAD_WARNING);
    }
  }

  /** トレーナーからの案（案のカード・予約表示・バナー用）。日付は yyyy-MM-dd。 */
  public record ProposalResponse(
      Long id,
      String name,
      int totalWeeks,
      String trainerName,
      String sentDate,
      String contentUpdatedDate,
      boolean contentUpdatedAfterResponse,
      String scheduledStartDate,
      String scheduledAfterCycleName) {
    static ProposalResponse from(PeriodizationViews.ProposalView v) {
      boolean updatedAfter =
          v.contentUpdatedAt() != null
              && (v.respondedAt() == null || v.contentUpdatedAt().isAfter(v.respondedAt()));
      return new ProposalResponse(
          v.id(),
          v.name(),
          v.totalWeeks(),
          v.trainerName(),
          v.sentAt() != null ? v.sentAt().toLocalDate().toString() : null,
          v.contentUpdatedAt() != null ? v.contentUpdatedAt().toLocalDate().toString() : null,
          updatedAfter,
          v.scheduledStartDate() != null ? v.scheduledStartDate().toString() : null,
          v.scheduledAfterCycleName());
    }
  }

  /** 返事待ち（通常は最新1件）と予約中の案。 */
  public record ProposalsResponse(
      List<ProposalResponse> pending, List<ProposalResponse> scheduled) {
    public static ProposalsResponse from(PeriodizationViews.TraineeProposals p) {
      return new ProposalsResponse(
          p.pending().stream().map(ProposalResponse::from).toList(),
          p.scheduled().stream().map(ProposalResponse::from).toList());
    }
  }

  /** 案の中身（週ごとの強度・曜日ごとの部位と種目）。 */
  public record ProposalContentResponse(
      String name, int totalWeeks, List<ContentWeek> weeks, List<ContentDay> days) {
    public record ContentWeek(int weekNumber, Double targetIntensityPct, boolean deload) {}

    public record ContentDay(
        int weekNumber, String dayOfWeek, String partCode, List<ItemResponse> items) {}

    public static ProposalContentResponse from(PeriodizationViews.CustomCycleInput c) {
      return new ProposalContentResponse(
          c.name(),
          c.totalWeeks(),
          c.weeks().stream()
              .map(
                  w ->
                      new ContentWeek(
                          w.weekNumber(),
                          w.targetIntensityPct() != null
                              ? w.targetIntensityPct().doubleValue()
                              : null,
                          Boolean.TRUE.equals(w.deload())))
              .toList(),
          c.days().stream()
              .map(
                  d ->
                      new ContentDay(
                          d.weekNumber(),
                          d.dayOfWeek(),
                          d.partCode(),
                          d.items().stream()
                              .map(i -> new ItemResponse(i.itemName(), i.targetSets(), null))
                              .toList()))
              .toList());
    }
  }

  public record RespondRequest(
      @NotNull com.example.traning.periodization.ProposalResponse response) {}

  /** 作成・継続したサイクルのID。GO_FREEFORMの場合はnull。 */
  public record CycleIdResponse(Long cycleId) {}
}
