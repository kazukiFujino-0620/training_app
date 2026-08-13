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
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private String source;
  }
}
