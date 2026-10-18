package com.example.traning.mobile.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 各ヘルスケア指標の最新値をまとめて返すレスポンス（ita3-1、表示画面用）。データが無い項目はnull。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthSummaryResponse {

  private WeightSummary weight;
  private StepsSummary steps;
  private HeartRateSummary heartRate;
  private CaloriesSummary calories;
  private SleepSummary sleep;
  private RestingHeartRateSummary restingHeartRate;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class WeightSummary {
    private LocalDate date;
    private Double weightKg;
    private Double bodyFatPct;
    private String source;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StepsSummary {
    private LocalDate date;
    private Integer stepCount;
    private String source;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HeartRateSummary {
    private LocalDate date;
    private Integer avgBpm;
    private Integer minBpm;
    private Integer maxBpm;
    private String source;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CaloriesSummary {
    private LocalDate date;
    private Double activeCalories;
    private Double totalCalories;
    private String source;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SleepSummary {
    private LocalDate date;
    /** 手動入力の記録ではnull（機能見直し-1-#5）。 */
    private LocalDateTime startTime;

    /** 手動入力の記録ではnull（機能見直し-1-#5）。 */
    private LocalDateTime endTime;

    private Integer durationMinutes;
    private String source;

    /** 直近4週間（前日まで）の平均睡眠時間（分）。記録が無ければnull（機能見直し-1-#5）。 */
    private Integer averageMinutesPast4Weeks;

    /** 上記平均の元になった記録日数。 */
    private int averageDaysPast4Weeks;
  }

  /** 安静時心拍（機能見直し-1-#5）。絶対値での良し悪し判定はせず、個人の過去平均との比較のみに使う。 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RestingHeartRateSummary {
    private LocalDate date;
    private Integer restingBpm;
    private String source;

    /** 直近4週間（前日まで）の平均安静時心拍（bpm）。記録が無ければnull。 */
    private Integer averageBpmPast4Weeks;

    /** 上記平均の元になった記録日数。 */
    private int averageDaysPast4Weeks;
  }
}
